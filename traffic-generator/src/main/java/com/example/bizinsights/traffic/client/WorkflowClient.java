package com.example.bizinsights.traffic.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WorkflowClient {

    private final HttpClient httpClient;

    public WorkflowClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public WorkflowClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpResponse<String> startWorkflow(String browseUrl, String orderId, String failAtStage)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(browseUrl))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("X-Order-Id", orderId)
                .timeout(Duration.ofSeconds(60));

        if (failAtStage != null && !failAtStage.isEmpty()) {
            builder.header("X-Fail-At-Stage", failAtStage);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> cancelOrder(String fulfillmentCancelUrl, String orderId)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fulfillmentCancelUrl))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("X-Order-Id", orderId)
                .timeout(Duration.ofSeconds(60))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
