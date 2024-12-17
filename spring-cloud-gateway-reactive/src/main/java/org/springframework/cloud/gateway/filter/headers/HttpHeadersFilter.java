package org.springframework.cloud.gateway.filter.headers;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.newrelic.instrumentation.labs.spring.cloud.gw.SpringGatewayHeaders;

@Weave(type = MatchType.Interface)
public abstract class HttpHeadersFilter {

	public static HttpHeaders filterRequest(List<HttpHeadersFilter> filters, ServerWebExchange exchange) {
		HttpHeaders result = Weaver.callOriginal();
		
		NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(new SpringGatewayHeaders(result));
		
		return result;
		
	}
}
