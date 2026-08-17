package com.newrelic.instrumentation.labs.spring.cloud.gw;

public class NRCancelRunnable implements Runnable {

	private NRHolder holder = null;

	public NRCancelRunnable(NRHolder h) {
		holder = h;
	}

	@Override
	public void run() {
		if(holder != null) {
			holder.end();
		}
	}

}
