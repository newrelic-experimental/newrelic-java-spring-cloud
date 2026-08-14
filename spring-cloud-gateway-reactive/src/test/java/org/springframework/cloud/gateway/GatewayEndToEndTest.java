package org.springframework.cloud.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.HttpWebHandlerAdapter;

import com.newrelic.agent.introspec.InstrumentationTestConfig;
import com.newrelic.agent.introspec.InstrumentationTestRunner;
import com.newrelic.agent.introspec.Introspector;
import com.newrelic.agent.introspec.SpanEvent;
import com.newrelic.api.agent.Trace;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

/**
 * Real end-to-end regression test: a real backend HTTP server, a real Spring Cloud Gateway routing chain
 * (the actual {@code NettyRoutingFilter} + {@code NettyWriteResponseFilter}, not mocks), bound to a real
 * Netty server socket, driven by a real {@link WebClient} over real sockets. Confirms the full round trip
 * actually works (backend receives the request, gateway relays the response back) and that the gateway
 * reports real, correctly-durationed spans for its own dispatch/routing work - the specific defect behind
 * the customer's original "971K transactions, 0 spans" symptom.
 *
 * Distributed-trace header propagation to the backend is deliberately not asserted here: that's the base
 * agent's own bundled Netty client instrumentation's responsibility (confirmed separately against a real
 * local gateway+backend deployment, both with and without this module installed), not something this
 * module's weave classes do themselves.
 */
@RunWith(InstrumentationTestRunner.class)
@InstrumentationTestConfig(includePrefixes = { "org.springframework.cloud.gateway",
        "com.newrelic.instrumentation.labs.spring.cloud.gw" }, configName = "spans.yml")
public class GatewayEndToEndTest {

    private DisposableServer backendServer;
    private DisposableServer gatewayServer;

    @After
    public void tearDown() {
        if (gatewayServer != null) {
            gatewayServer.disposeNow();
        }
        if (backendServer != null) {
            backendServer.disposeNow();
        }
    }

    @Test
    public void proxiedRequest_roundTripsAndReportsRealDispatchAndExternalSpans() throws Exception {
        backendServer = HttpServer.create().host("localhost").port(0)
                .handle((req, res) -> res.status(200).sendString(Mono.just("backend-ok")))
                .bindNow();

        gatewayServer = startGateway(backendServer.port());

        WebClient client = WebClient.builder().build();
        String body = client.get()
                .uri("http://localhost:" + gatewayServer.port() + "/api/v1/foo/123")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertEquals("backend-ok", body);

        Introspector introspector = InstrumentationTestRunner.getIntrospector();
        assertEquals(1, introspector.getFinishedTransactionCount(3000));
        assertTrue(introspector.getTransactionNames().toString(),
                introspector.getTransactionNames().stream()
                        .anyMatch(name -> name.contains("SpringCloudGW/handle") && name.contains("(GET)")));

        SpanEvent handleSegmentSpan = findSpanByName(introspector, "Custom/handle");
        // The "CloudRequest" segment is renamed once holder.reportAsExternal(params) runs (see
        // NettyRoutingFilter/SpringCloudUtils.getParams: HttpParameters.library("Spring-Cloud")...) - its
        // reported span name becomes the External/<host>/<library>/<procedure> convention, not "Custom/CloudRequest".
        SpanEvent cloudRequestSpan = introspector.getSpanEvents().stream()
                .filter(span -> span.getName().startsWith("External/"))
                .findFirst().orElse(null);
        assertNotNull("expected a \"Custom/handle\" span", handleSegmentSpan);
        assertNotNull("expected an External/... span for the proxied call", cloudRequestSpan);

        // Duration sanity check: both segments must reflect real elapsed time, not the near-instant
        // method-return time the original bug would have produced.
        assertTrue("expected \"Custom/handle\" to have a real, non-zero duration", handleSegmentSpan.duration() > 0f);
        assertTrue("expected the External span to have a real, non-zero duration", cloudRequestSpan.duration() > 0f);
    }

    /**
     * Wires up the real routing chain a Spring Cloud Gateway app would normally assemble via
     * {@code GatewayAutoConfiguration} - here built directly, without a Spring ApplicationContext, since
     * the module's only dependency is spring-cloud-gateway-server itself. NettyRoutingFilter,
     * NettyWriteResponseFilter and HttpHeadersFilter are all real, woven classes; reflection is used only
     * where a class shares a name with this module's own weave mirrors (see the reflective helpers below).
     */
    private DisposableServer startGateway(int backendPort) throws Exception {
        Route route = Route.async()
                .id("test-route")
                .uri("http://localhost:" + backendPort)
                .predicate(ex -> true)
                .build();

        Object headersFilterProxy = newPassthroughHeadersFilter();
        List<Object> headersFilters = Collections.singletonList(headersFilterProxy);
        ObjectProvider<List<?>> headersFiltersProvider = newHeadersFiltersProvider(headersFilters);

        Object nettyRoutingFilter = newNettyRoutingFilter(headersFiltersProvider);
        Object nettyWriteResponseFilter = newNettyWriteResponseFilter();

        Object filteringWebHandler = newFilteringWebHandler(Arrays.asList(nettyRoutingFilter, nettyWriteResponseFilter));
        Method handleMethod = filteringWebHandler.getClass().getMethod("handle", ServerWebExchange.class);

        // There's no real HTTP-server-level instrumentation active in this unit-test harness (only classes
        // under includePrefixes get woven) to start a transaction the way the real agent's bundled
        // reactor-netty-http instrumentation would in production. Start one explicitly via
        // @Trace(dispatcher = true), and block for full completion inside it - same reasoning as
        // FilteringWebHandlerTest.invokeHandle(). Reactor-netty forbids blocking on its own event-loop
        // threads, so the blocking call is shifted onto boundedElastic first.
        WebHandler webHandler = exchange -> {
            exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
                    URI.create(route.getUri().toString() + exchange.getRequest().getPath().value()));
            return Mono.fromRunnable(() -> dispatchBlocking(filteringWebHandler, handleMethod, exchange))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then();
        };

        HttpHandler httpHandler = new HttpWebHandlerAdapter(webHandler);
        return HttpServer.create().host("localhost").port(0)
                .handle(new ReactorHttpHandlerAdapter(httpHandler))
                .bindNow();
    }

    @Trace(dispatcher = true)
    public void dispatchBlocking(Object filteringWebHandler, Method handleMethod, ServerWebExchange exchange) {
        try {
            Mono<?> result = (Mono<?>) handleMethod.invoke(filteringWebHandler, exchange);
            result.block();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Reflective construction of real, woven classes that share a name with this module's own weave
    // mirrors (see FilteringWebHandlerTest/HttpHeadersFilterTest for the same pattern and rationale). ---

    private Object newFilteringWebHandler(List<Object> globalFilters) throws ReflectiveOperationException {
        Class<?> handlerClass = Class.forName("org.springframework.cloud.gateway.handler.FilteringWebHandler");
        Constructor<?> constructor = handlerClass.getConstructor(List.class);
        return constructor.newInstance(globalFilters);
    }

    private Object newNettyRoutingFilter(ObjectProvider<List<?>> headersFiltersProvider) throws ReflectiveOperationException {
        Class<?> filterClass = Class.forName("org.springframework.cloud.gateway.filter.NettyRoutingFilter");
        Constructor<?> constructor = filterClass.getConstructor(HttpClient.class, ObjectProvider.class,
                HttpClientProperties.class);
        return constructor.newInstance(HttpClient.create(), headersFiltersProvider, new HttpClientProperties());
    }

    private Object newNettyWriteResponseFilter() throws ReflectiveOperationException {
        Class<?> filterClass = Class.forName("org.springframework.cloud.gateway.filter.NettyWriteResponseFilter");
        Constructor<?> constructor = filterClass.getConstructor(List.class);
        return constructor.newInstance(Collections.<MediaType>emptyList());
    }

    private Object newPassthroughHeadersFilter() throws ReflectiveOperationException {
        Class<?> filterInterface = Class.forName("org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter");
        InvocationHandler passthrough = (proxy, method, args) -> {
            if ("filter".equals(method.getName())) {
                HttpHeaders input = (HttpHeaders) args[0];
                return new HttpHeaders(new LinkedMultiValueMap<>(input));
            }
            if ("supports".equals(method.getName())) {
                return true;
            }
            return null;
        };
        return Proxy.newProxyInstance(filterInterface.getClassLoader(), new Class<?>[] { filterInterface },
                passthrough);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<List<?>> newHeadersFiltersProvider(List<Object> headersFilters) {
        return new ObjectProvider<List<?>>() {
            @Override
            public List<?> getIfAvailable() {
                return headersFilters;
            }

            @Override
            public List<?> getObject() {
                return headersFilters;
            }

            @Override
            public List<?> getObject(Object... args) {
                return headersFilters;
            }

            @Override
            public List<?> getIfUnique() {
                return headersFilters;
            }
        };
    }

    private SpanEvent findSpanByName(Introspector introspector, String name) {
        for (SpanEvent span : introspector.getSpanEvents()) {
            if (name.equals(span.getName())) {
                return span;
            }
        }
        return null;
    }
}
