package com.newrelic.labs;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.weaver.MatchType;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

@Weave(type = MatchType.ExactClass, originalName = "org.springframework.cloud.gateway.handler.FilteringWebHandler")
public abstract class FilteringWebHandler_Instrumentation {

    @Trace(dispatcher = true)
    public Mono<Void> handle(ServerWebExchange exchange) {

        final String path = exchange.getRequest().getPath().value();

        final String simplifiedPath;

        final List<String> splitPath = Arrays.asList(path.split("/"));

        if (splitPath.isEmpty()) {
            simplifiedPath = path;
        } else {
            final StringBuilder builder = new StringBuilder();
            for (String p : splitPath) {
                builder.append("/").append(normalizePath(p));
            }
            simplifiedPath = builder.toString();
        }


        NewRelic.setTransactionName("Web", simplifiedPath);
        NewRelic.getAgent().getLogger().log(Level.FINER,
                "spring-cloud-gateway Instrumentation: Setting web transaction name to " + simplifiedPath);
        Mono<Void> ret = Weaver.callOriginal();

        return ret;
    }

    public String normalizePath(String path) {

        final Pattern versionPattern = Pattern.compile("[vV][0-9]{1,}");
        final Pattern idPattern = Pattern.compile("^(?=[^\\s]*?[0-9])[-{}().:_|0-9]+$");
        final Pattern codPattern = Pattern.compile("^(?=[^\\s]*?[0-9])(?=[^\\s]*?[a-zA-Z])(?!\\{id\\})[-{}().:_|a-zA-Z0-9]*$");

        if (versionPattern.matcher(path).matches()) {
            return path.replaceAll(versionPattern.toString(), "{version}");
        } else if (idPattern.matcher(path).matches()) {
            return path.replaceAll(idPattern.toString(), "{id}");
        } else if (codPattern.matcher(path).matches()) {
            return path.replaceAll(codPattern.toString(), "{cod}");
        } else {
            return path;
        }
    }

}
