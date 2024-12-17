package org.springframework.cloud.gateway.handler;

import java.util.logging.Level;

import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import com.newrelic.instrumentation.labs.spring.cloud.gw.SpringCloudUtils;

import reactor.core.publisher.Mono;

@Weave
public abstract class FilteringWebHandler {

    @Trace
    public Mono<Void> handle(ServerWebExchange exchange) {

    	String simplifiedPath = SpringCloudUtils.getSimpliedPath(exchange);
        NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "SpringCloudGW", new String[]{"handle", simplifiedPath});
        NewRelic.getAgent().getLogger().log(Level.FINEST, "spring-cloud-gateway Instrumentation: Setting web transaction name to " + simplifiedPath);

        return Weaver.callOriginal();
    }


}
