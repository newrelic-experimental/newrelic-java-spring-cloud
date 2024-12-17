

package com.newrelic.instrumentation.labs.spring.cloud.gw;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import com.newrelic.api.agent.HttpParameters;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

public class SpringCloudUtils {
	
    private static final Pattern versionPattern = Pattern.compile("[vV][0-9]{1,}");
    private static final Pattern idPattern = Pattern.compile("^(?=[^\\s]*?[0-9])[-{}().:_|0-9]+$");
    private static final Pattern codPattern = Pattern.compile("^(?=[^\\s]*?[0-9])(?=[^\\s]*?[a-zA-Z])(?!\\{id\\}).*$");



	public static void recordValue(Map<String,Object> attributes, String key, Object value) {
		if(key != null && !key.isEmpty() && value != null) {
			attributes.put(key, value);
		}
	}

	public static HttpParameters getParams(ServerWebExchange exchange) {
		try {
			URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
			ServerHttpRequest request = exchange.getRequest();
			HttpMethod method = request.getMethod();
			HttpParameters params = HttpParameters.library("Spring-Cloud").uri(requestUrl).procedure(method.name()).noInboundHeaders().build();
			return params;
		} catch (Exception e) {
		}
		
		return null;
	}
	
	public static String getSimpliedPath(ServerWebExchange exchange)  {
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

		return simplifiedPath;
	}
	
	public static boolean skip(ServerWebExchange exchange) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return true;
		}
		return false;
	}
	
	public static boolean skipWS(ServerWebExchange exchange) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"ws".equals(scheme) && !"wss".equals(scheme))) {
			return true;
		}
		return false;
	}

}
