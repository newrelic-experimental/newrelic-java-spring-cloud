package org.springframework.cloud.gateway.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.newrelic.agent.introspec.InstrumentationTestConfig;
import com.newrelic.agent.introspec.InstrumentationTestRunner;
import com.newrelic.agent.introspec.Introspector;
import com.newrelic.agent.introspec.SpanEvent;
import com.newrelic.agent.introspec.TracedMetricData;
import com.newrelic.api.agent.Trace;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Regression test for the "missing parent span" defect: {@code FilteringWebHandler.handle()} returns a
 * lazily-executed {@code Mono<Void>} - none of the real routing work happens until something subscribes,
 * possibly on a different thread. Before the fix, a bare {@code @Trace} closed the segment the instant the
 * Mono was constructed, well before real completion, so no usable span was ever reported for this method
 * even though 100% of the transactions it named were reported.
 *
 * The single custom {@link GlobalFilter} below deliberately delays and hops threads (Schedulers.parallel())
 * before completing, so the assertions below can only pass if the span's duration reflects real async
 * completion rather than method-return time.
 */
@RunWith(InstrumentationTestRunner.class)
@InstrumentationTestConfig(includePrefixes = { "org.springframework.cloud.gateway",
        "com.newrelic.instrumentation.labs.spring.cloud.gw" }, configName = "spans.yml")
public class FilteringWebHandlerTest {

    private static final long DELAY_MILLIS = 50;

    // getSimplifiedPath() splits on "/", so a leading "/" in the request path produces a leading empty
    // segment - this doubled leading slash is intentional and matches real production transaction names
    // (e.g. "WebTransaction/SpringCloudGW/handle///api/settlement/{version}/... (GET)" in production,
    // where the transaction is a real web transaction; here it's driven by a plain @Trace(dispatcher=true)
    // test helper rather than a real HTTP entry point, so the agent categorizes it as "OtherTransaction").
    private static final String EXPECTED_TXN_NAME = "OtherTransaction/SpringCloudGW/handle///api/{version}/foo/{id} (GET)";
    private static final String HANDLE_METHOD_METRIC = "Java/org.springframework.cloud.gateway.handler.FilteringWebHandler/handle";
    private static final String HANDLE_SEGMENT_METRIC = "Custom/handle";

    @Test
    public void handle_reportsSpanCoveringRealAsyncCompletion_notMethodReturnTime() {
        ServerWebExchange exchange = buildExchange("/api/v1/foo/123");

        GlobalFilter delayingFilter = (ex, chain) -> Mono.delay(Duration.ofMillis(DELAY_MILLIS))
                .then(chain.filter(ex))
                .subscribeOn(Schedulers.parallel());

        invokeHandle(Collections.singletonList(delayingFilter), exchange);

        Introspector introspector = InstrumentationTestRunner.getIntrospector();
        assertEquals(1, introspector.getFinishedTransactionCount(3000));

        assertTrue("expected transaction \"" + EXPECTED_TXN_NAME + "\" in " + introspector.getTransactionNames(),
                introspector.getTransactionNames().contains(EXPECTED_TXN_NAME));

        Map<String, TracedMetricData> metrics = introspector.getMetricsForTransaction(EXPECTED_TXN_NAME);
        assertTrue("expected metric \"" + HANDLE_METHOD_METRIC + "\" in " + metrics.keySet(),
                metrics.containsKey(HANDLE_METHOD_METRIC));
        assertEquals(1, metrics.get(HANDLE_METHOD_METRIC).getCallCount());

        SpanEvent outerHandleSpan = findSpanByName(introspector, HANDLE_METHOD_METRIC);
        SpanEvent segmentSpan = findSpanByName(introspector, HANDLE_SEGMENT_METRIC);
        assertNotNull("expected a span named \"" + HANDLE_METHOD_METRIC + "\"", outerHandleSpan);
        assertNotNull("expected a span named \"" + HANDLE_SEGMENT_METRIC + "\"", segmentSpan);

        // The whole point of the fix: the segment's duration must reflect the real 50ms async delay,
        // not the ~instant method-return time a bare @Trace on a Mono-returning method would report.
        float minExpectedSeconds = (DELAY_MILLIS / 1000f) * 0.9f;
        assertTrue("expected \"" + HANDLE_SEGMENT_METRIC + "\" duration (" + segmentSpan.duration()
                        + "s) to reflect the real " + DELAY_MILLIS + "ms async delay, not method-return time",
                segmentSpan.duration() >= minExpectedSeconds);

        // Nesting must survive the async boundary: the segment's parent is still handle()'s own span.
        assertEquals("expected \"" + HANDLE_SEGMENT_METRIC + "\" to nest under \"" + HANDLE_METHOD_METRIC + "\"",
                outerHandleSpan.getGuid(), segmentSpan.parentId());
    }

    private ServerWebExchange buildExchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Route route = Route.async()
                .id("test-route")
                .uri("http://localhost:1")
                .predicate(ex -> true)
                .build();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        return exchange;
    }

    // FilteringWebHandler is invoked reflectively, by class name only, rather than referenced as a static
    // type: this module's own @Weave mirror of org.springframework.cloud.gateway.handler.FilteringWebHandler
    // (same fully-qualified name, by design, matching how NettyRoutingFilter/etc. are woven in this module)
    // sits on this same source set's compiled output, and would otherwise shadow the real, concrete class
    // from the spring-cloud-gateway-server dependency at compile time - the InstrumentationTestRunner's
    // weaving classloader resolves the real target class correctly at runtime regardless.
    @Trace(dispatcher = true)
    public void invokeHandle(List<GlobalFilter> globalFilters, ServerWebExchange exchange) {
        try {
            Class<?> handlerClass = Class.forName("org.springframework.cloud.gateway.handler.FilteringWebHandler");
            Constructor<?> constructor = handlerClass.getConstructor(List.class);
            Object handler = constructor.newInstance(globalFilters);
            Method handleMethod = handlerClass.getMethod("handle", ServerWebExchange.class);
            Mono<?> result = (Mono<?>) handleMethod.invoke(handler, exchange);
            result.block();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
