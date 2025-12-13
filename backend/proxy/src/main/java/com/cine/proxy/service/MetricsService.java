package com.cine.proxy.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.LongAdder;

@Service
public class MetricsService {

    private final LongAdder eventsReceived = new LongAdder();
    private final LongAdder eventsProcessed = new LongAdder();

    public void incrementEventsReceived() {
        eventsReceived.increment();
    }

    public void incrementEventsProcessed() {
        eventsProcessed.increment();
    }

    public long getEventsReceived() {
        return eventsReceived.sum();
    }

    public long getEventsProcessed() {
        return eventsProcessed.sum();
    }
}
