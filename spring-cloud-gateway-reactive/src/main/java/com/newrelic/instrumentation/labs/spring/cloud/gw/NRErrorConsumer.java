package com.newrelic.instrumentation.labs.spring.cloud.gw;

import java.util.function.Consumer;

import com.newrelic.api.agent.NewRelic;

public class NRErrorConsumer implements Consumer<Throwable> {
	
	private NRHolder holder = null;
	
	public NRErrorConsumer(NRHolder h) {
		holder = h;
	}
	

	@Override
	public void accept(Throwable t) {
		if(t != null) {
			NewRelic.noticeError(t);
		}
		if(holder != null) {
			holder.ignore();
		}
	}

}
