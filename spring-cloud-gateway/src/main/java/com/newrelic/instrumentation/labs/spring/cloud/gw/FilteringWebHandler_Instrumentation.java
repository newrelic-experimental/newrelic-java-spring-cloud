package com.newrelic.instrumentation.labs.spring.cloud.gw;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.logging.Level;
import java.util.regex.Pattern;

@Weave(type = MatchType.ExactClass, originalName = "org.springframework.cloud.gateway.handler.FilteringWebHandler")
public abstract class FilteringWebHandler_Instrumentation {

    @Trace(dispatcher = true)
    public Mono<Void> handle(ServerWebExchange exchange) {

        try {
            final Pattern versionPattern = Pattern.compile("[vV][0-9]{1,}");
            final Pattern idPattern = Pattern.compile("^(?=[^\\s]*?[0-9])[-{}().:_|0-9]+$");
            final Pattern codPattern = Pattern.compile("^(?=[^\\s]*?[0-9])(?=[^\\s]*?[a-zA-Z])(?!\\{id\\}).*$");

            String path = exchange.getRequest().getPath().value();

            String simplifiedPath = path;

            final String[] splitPath = path.split("/");

            if (splitPath.length > 0) {
                simplifiedPath = "";
                for (String p : splitPath) {
                    if (versionPattern.matcher(p).matches()) {
                        simplifiedPath = simplifiedPath.concat("/").concat(p.replaceAll(versionPattern.toString(), "{version}"));
                    } else if (idPattern.matcher(p).matches()) {
                        simplifiedPath = simplifiedPath.concat("/").concat(p.replaceAll(idPattern.toString(), "{id}"));
                    } else if (codPattern.matcher(p).matches()) {
                        simplifiedPath = simplifiedPath.concat("/").concat(p.replaceAll(codPattern.toString(), "{cod}"));
                    } else {
                        simplifiedPath = simplifiedPath.concat("/").concat(p);
                    }
                }
            }

            //NewRelic.setTransactionName("Web", simplifiedPath);
            NewRelic.getAgent().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "SpringCloudGW", new String[]{"handle", simplifiedPath});
            NewRelic.getAgent().getLogger().log(Level.FINEST,
                    "spring-cloud-gateway Instrumentation: Setting web transaction name to " + simplifiedPath);
        } catch (Exception e) {
            System.out.println("ERROR spring-cloud-gateway Instrumentation: " + e.getMessage());
        }

        return Weaver.callOriginal();
    }


}
