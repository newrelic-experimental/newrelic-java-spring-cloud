package org.springframework.cloud.gateway.server.mvc.handler;

import java.net.URI;

import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TracedMethod;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

@Weave
public class ProxyExchangeHandlerFunction {

	@Trace
	public ServerResponse handle(ServerRequest serverRequest) {
		URI uri = serverRequest.uri();
		TracedMethod traced = NewRelic.getAgent().getTracedMethod();
		traced.addCustomAttribute("RequestURI", uri.toASCIIString());
		traced.addCustomAttribute("Method", serverRequest.methodName());
		return Weaver.callOriginal();
	}
}
