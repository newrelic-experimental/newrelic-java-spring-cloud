package com.newrelic.instrumentation.labs.spring.cloud.gw;

import com.newrelic.api.agent.ExternalParameters;
import com.newrelic.api.agent.Segment;

public class NRHolder {

	private Segment segment = null;
	
	public NRHolder(Segment seg) {
		segment = seg;
	}
	
	public boolean reportAsExternal(ExternalParameters params) {
		if(segment != null) {
			segment.reportAsExternal(params);
			return true;
		}
		return false;
	}
	
	public void ignore() {
		if(segment != null) {
			segment.ignore();
			segment = null;
		}
	}
	
	public void end() {
		if(segment != null) {
			segment.end();
			segment = null;
		}
	}
}
