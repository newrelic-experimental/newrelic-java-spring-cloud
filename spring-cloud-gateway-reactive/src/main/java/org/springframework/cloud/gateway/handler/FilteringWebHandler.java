package org.springframework.cloud.gateway.handler;

import java.util.logging.Level;

import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Segment;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRCancelRunnable;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRCompletionConsumer;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRErrorConsumer;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRHolder;
import com.newrelic.instrumentation.labs.spring.cloud.gw.SpringCloudUtils;

import reactor.core.publisher.Mono;

@Weave
public abstract class FilteringWebHandler {

    @Trace
    public Mono<Void> handle(ServerWebExchange exchange) {

    	String simplifiedPath = SpringCloudUtils.getSimplifiedPath(exchange);
    	// Append the HTTP method as a trailing "(METHOD)" suffix so transactions for the same route but
    	// different verbs (GET vs POST vs ...) are distinguishable in APM/NRQL instead of all collapsing
    	// into one indistinguishable "handle/<path>" name.
    	String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : null;
    	String pathWithMethod = (method == null || method.isEmpty()) ? simplifiedPath : simplifiedPath + " (" + method + ")";
        NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "SpringCloudGW", new String[]{"handle", pathWithMethod});
        NewRelic.getAgent().getLogger().log(Level.FINEST, "spring-cloud-gateway Instrumentation: Setting web transaction name to " + pathWithMethod);

        // The real handle() below only builds and returns a not-yet-subscribed Mono - none of the
        // actual filter chain/routing work happens until something downstream subscribes, possibly
        // on a different thread. A bare @Trace here would close its segment the instant this method
        // returns (i.e. the instant the Mono is constructed), long before the real work completes.
        // Manage a Segment explicitly instead, and end it when the Mono actually finishes.
        Segment segment = NewRelic.getAgent().getTransaction().startSegment("handle");
        NRHolder holder = new NRHolder(segment);

        Mono<Void> result = Weaver.callOriginal();

        return result.doOnSuccess(new NRCompletionConsumer(holder))
                .doOnError(new NRErrorConsumer(holder))
                .doOnCancel(new NRCancelRunnable(holder));
    }


}
