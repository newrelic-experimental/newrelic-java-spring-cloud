package com.newrelic.labs;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

import reactor.core.publisher.Mono;

@Weave(type = MatchType.Interface, originalName="org.springframework.cloud.gateway.filter.GlobalFilter")
public abstract class GlobalFilter_Instrumentation {

	@Trace
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return Weaver.callOriginal();
	}
}
