package org.springframework.cloud.gateway.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.newrelic.instrumentation.labs.spring.cloud.gw.Util;

import reactor.core.publisher.Mono;

@Weave(type = MatchType.BaseClass)
public abstract class RoutePredicateHandlerMapping {

	@Trace(dispatcher = true)
	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {

		Map<String, Object> attrs = new HashMap<>();
		Mono<Route> result =null;

		NewRelic.getAgent().getTracedMethod().setMetricName(new String[] {"Custom", "SpringCloudGW", "RoutePredicateHandlerMapping", getClass().getSimpleName(), "lookupRoute"});

		if (exchange != null ) {
			// Modify this section based on the methods and properties of DistributionRequest
			attrs = exchange.getAttributes();
			String path = exchange.getRequest().getPath().value();
			Util.recordValue(attrs, "uri.path", path);

		}

		if (attrs != null) {
			NewRelic.getAgent().getTracedMethod().addCustomAttributes(attrs);
		}


		result = Weaver.callOriginal();


		return result;

	}

}

