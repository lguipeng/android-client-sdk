package com.launchdarkly.android;


import android.util.Log;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.MessageEvent;

import java.io.Closeable;
import java.io.IOException;

import okhttp3.Headers;

class StreamProcessor implements Closeable {
    private static final String TAG = "LDStreamProcessor";

    private EventSource es;
    private final LDConfig config;
    private final FeatureFlagUpdater updater;
    private volatile boolean running = false;

    StreamProcessor(LDConfig config, FeatureFlagUpdater updater) {
        this.config = config;
        this.updater = updater;
    }

    synchronized void start() {
        if (!running) {
            close();
            es = createEventSourceClient();
            es.start();
            running = true;
        }
    }

    synchronized void stop() {
        close();
        running = false;
    }

    private EventSource createEventSourceClient() {
        Headers headers = new Headers.Builder()
                .add("Authorization", config.getMobileKey())
                .add("User-Agent", LDConfig.USER_AGENT_HEADER_VALUE)
                .add("Accept", "text/event-stream")
                .build();

        return new EventSource.Builder(handler, java.net.URI.create(config.getStreamUri().toString() + "/mping"))
                .headers(headers)
                .build();
    }


    EventHandler handler = new EventHandler() {
        @Override
        public void onOpen() throws Exception {
            Log.i(TAG, "Started LaunchDarkly EventStream");
        }

        @Override
        public void onMessage(String name, MessageEvent event) throws Exception {
            Log.d(TAG, "onMessage: name: " + name + " event: " + event.getData());
            updater.update();
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, "Encountered EventStream error: " + t.getMessage(), t);
        }
    };


    @Override
    public void close() {
        if (es != null) {
            try {
                es.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception caught when closing stream.", e);
            }
        }
    }
}
