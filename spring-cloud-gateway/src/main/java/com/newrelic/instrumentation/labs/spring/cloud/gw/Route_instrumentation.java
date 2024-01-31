package com.newrelic.instrumentation.labs.spring.cloud.gw;

import java.net.URI;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.Route.AsyncBuilder;
import org.springframework.cloud.gateway.route.Route.Builder;

import com.newrelic.api.agent.GenericParameters;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;

@Weave(type = MatchType.ExactClass, originalName = "org.springframework.cloud.gateway.route.Route")
public abstract class Route_instrumentation {

	@Trace(dispatcher = true )
	public static Builder builder(RouteDefinition routeDefinition) {

		URI uri= null;
		Builder result=null;

		uri = routeDefinition.getUri();

		NewRelic.getAgent().getTracedMethod().setMetricName(new String[] {"Custom", "SpringCloudGW", "Route", "builder"});

		if (uri != null ) {
			
			GenericParameters params;
			params = GenericParameters.library("SpringCloudGW").uri(uri).procedure("builder").build();
			NewRelic.getAgent().getTracedMethod().reportAsExternal(params);
		}


		result= Weaver.callOriginal();
		return result;



	}

	@Trace(dispatcher = true)
	public static AsyncBuilder async(RouteDefinition routeDefinition) {

		URI uri= null;
		AsyncBuilder result=null;

		uri = routeDefinition.getUri();

		NewRelic.getAgent().getTracedMethod().setMetricName(new String[] {"Custom", "SpringCloudGW", "Route", "async"});

		if (uri != null ) {

			GenericParameters params;
			params = GenericParameters.library("SpringCloudGW").uri(uri).procedure("async").build();
			NewRelic.getAgent().getTracedMethod().reportAsExternal(params);
		}


		result= Weaver.callOriginal();
		return result;



	}


}
