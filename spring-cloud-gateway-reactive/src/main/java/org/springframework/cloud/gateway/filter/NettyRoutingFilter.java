package org.springframework.cloud.gateway.filter;

import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.HttpParameters;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Segment;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRCompletionConsumer;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRErrorConsumer;
import com.newrelic.instrumentation.labs.spring.cloud.gw.NRHolder;
import com.newrelic.instrumentation.labs.spring.cloud.gw.SpringCloudUtils;

import reactor.core.publisher.Mono;

@Weave
public class NettyRoutingFilter {

	@Trace
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		boolean skip = SpringCloudUtils.skip(exchange);
		NRCompletionConsumer onCompleteConsumer = null;
		NRErrorConsumer onErrorConsumer = null;
		
		if(!skip) {
			HttpParameters params = SpringCloudUtils.getParams(exchange);
			Segment segment = NewRelic.getAgent().getTransaction().startSegment("CloudRequest");
			NRHolder holder = new NRHolder(segment);
			holder.reportAsExternal(params);
			onCompleteConsumer = new NRCompletionConsumer(holder);
			onErrorConsumer = new NRErrorConsumer(holder);
			
		}
		
		Mono<Void> resultMono = Weaver.callOriginal();
		
		if(!skip) {
			return resultMono.doOnSuccess(onCompleteConsumer).doOnError(onErrorConsumer);
		}

		return resultMono;
	}
}
