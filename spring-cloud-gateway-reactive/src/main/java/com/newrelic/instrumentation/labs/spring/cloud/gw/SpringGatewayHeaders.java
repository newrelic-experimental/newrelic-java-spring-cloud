package com.newrelic.instrumentation.labs.spring.cloud.gw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpHeaders;

import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;

public class SpringGatewayHeaders implements Headers {
	
	private HttpHeaders headers = null;
	
	public SpringGatewayHeaders(HttpHeaders h) {
		headers = h;
	}

	@Override
	public void addHeader(String name, String value) {
		if(headers != null) {
			headers.add(name, value);
		}
	}

	@Override
	public boolean containsHeader(String name) {
		return getHeaderNames().contains(name);
	}

	@Override
	public String getHeader(String name) {
		if(headers != null) {
			return headers.getFirst(name);
		}
		return null;
	}

	@Override
	public Collection<String> getHeaderNames() {
		if(headers != null) {
			return headers.keySet();
		}
		return Collections.emptyList();
	}

	@Override
	public HeaderType getHeaderType() {
		return HeaderType.HTTP;
	}

	@Override
	public Collection<String> getHeaders(String name) {
		if(headers != null) {
			return headers.get(name);
		}
		return null;
	}

	@Override
	public void setHeader(String name, String value) {
		if(headers != null) {
			headers.set(name, value);
		}
	}

}
