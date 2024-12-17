package com.newrelic.instrumentation.labs.spring.cloud.gw;

import java.util.function.Consumer;

public class NRCompletionConsumer implements Consumer<Void> {
	
	private NRHolder holder = null;
	
	public NRCompletionConsumer(NRHolder h) {
		holder = h;
	}
	

	@Override
	public void accept(Void t) {
		if(holder != null) {
			holder.end();
		}
	}

}
