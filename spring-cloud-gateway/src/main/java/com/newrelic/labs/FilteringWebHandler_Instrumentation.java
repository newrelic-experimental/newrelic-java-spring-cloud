package com.newrelic.labs;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

import reactor.core.publisher.Mono;

@Weave(type = MatchType.ExactClass, originalName = "org.springframework.cloud.gateway.handler.FilteringWebHandler")
public abstract class FilteringWebHandler_Instrumentation {

	@Trace(dispatcher = true)
	public Mono<Void> handle(ServerWebExchange exchange) {
		String path = exchange.getRequest().getPath().value();

		List<String> splitPath = Arrays.asList(path.split("/"));
		String simplifiedPath = path;
		if (!splitPath.isEmpty()) {

		}

		NewRelic.setTransactionName("Web", simplifiedPath);
		NewRelic.getAgent().getLogger().log(Level.FINER,
				"spring-cloud-gateway Instrumentation: Setting web transaction name to " + simplifiedPath);
		Mono<Void> ret = Weaver.callOriginal();

		return ret;
	}

}
