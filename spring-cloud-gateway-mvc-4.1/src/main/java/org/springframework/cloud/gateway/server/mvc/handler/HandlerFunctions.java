package org.springframework.cloud.gateway.server.mvc.handler;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

@Weave
public abstract class HandlerFunctions {

	@Weave
	static class LookupProxyExchangeHandlerFunction {
		
		private final URI uri = Weaver.callOriginal();
		
		@Trace
		public ServerResponse handle(ServerRequest serverRequest) {
			if(uri != null) {
				String path = uri.getPath();
				StringBuffer sb = new StringBuffer(path);
				
				Map<String, String> pathVars = serverRequest.pathVariables();
				if(pathVars != null && !pathVars.isEmpty()) {
					sb.append('?');
					Set<String> keys = pathVars.keySet();
					for(String key : keys) {
						sb.append(key);
						sb.append("=?");
					}
				}
				NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.FRAMEWORK_HIGH, false, "SoringGateway", "handle", sb.toString(),serverRequest.methodName());
			}
			
			return Weaver.callOriginal();
		}
	}
}
