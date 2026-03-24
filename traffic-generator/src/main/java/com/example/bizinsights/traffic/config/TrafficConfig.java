package com.example.bizinsights.traffic.config;

public record TrafficConfig(
        int requests,
        long delayMs,
        String failAtStage,
        double cancelRatio,
        String browseUrl,
        String fulfillmentCancelUrl
) {
}
