package com.example.bizinsights.traffic;

import com.example.bizinsights.traffic.client.WorkflowClient;
import com.example.bizinsights.traffic.store.OrderIdStore;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@Command(name = "traffic-generator", mixinStandardHelpOptions = true,
        description = "Generates traffic for the business insights demo workflow")
public class TrafficGeneratorApp implements Callable<Integer> {

    @Option(names = "--requests", defaultValue = "50",
            description = "Number of workflow requests to generate")
    private int requests;

    @Option(names = "--delay", defaultValue = "2000",
            description = "Delay between requests in milliseconds")
    private long delayMs;

    @Option(names = "--fail-at", defaultValue = "",
            description = "Stage name to inject failure at (e.g. payment)")
    private String failAtStage;

    @Option(names = "--cancel-ratio", defaultValue = "0.2",
            description = "Fraction of successful orders to cancel (0.0-1.0)")
    private double cancelRatio;

    @Option(names = "--browse-url", defaultValue = "http://localhost:8081/process",
            description = "URL of the browse service")
    private String browseUrl;

    @Option(names = "--fulfillment-url", defaultValue = "http://localhost:8085/cancel",
            description = "URL of the fulfillment cancel endpoint")
    private String fulfillmentCancelUrl;

    private final WorkflowClient client;
    private final OrderIdStore store;

    public TrafficGeneratorApp() {
        this.client = new WorkflowClient();
        this.store = new OrderIdStore();
    }

    public TrafficGeneratorApp(WorkflowClient client, OrderIdStore store) {
        this.client = client;
        this.store = store;
    }

    @Override
    public Integer call() {
        System.out.printf("Starting traffic generation: %d requests, delay=%dms, failAt=%s, cancelRatio=%.1f%n",
                requests, delayMs, failAtStage.isEmpty() ? "none" : failAtStage, cancelRatio);

        int successful = 0;
        int failed = 0;
        int cancelled = 0;

        for (int i = 1; i <= requests; i++) {
            String orderId = UUID.randomUUID().toString();

            try {
                System.out.printf("[%d/%d] Starting workflow for order: %s%n", i, requests, orderId);
                HttpResponse<String> response = client.startWorkflow(browseUrl, orderId, failAtStage);

                if (response.statusCode() == 200) {
                    successful++;
                    store.add(orderId);
                    System.out.printf("  -> Completed successfully%n");
                } else {
                    failed++;
                    System.out.printf("  -> Failed (HTTP %d): %s%n", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                failed++;
                System.out.printf("  -> Error: %s%n", e.getMessage());
            }

            if (cancelRatio > 0 && ThreadLocalRandom.current().nextDouble() < cancelRatio) {
                store.pickRandom().ifPresent(cancelOrderId -> {
                    try {
                        System.out.printf("  -> Cancelling order: %s%n", cancelOrderId);
                        HttpResponse<String> cancelResponse =
                                client.cancelOrder(fulfillmentCancelUrl, cancelOrderId);
                        System.out.printf("  -> Cancel result (HTTP %d): %s%n",
                                cancelResponse.statusCode(), cancelResponse.body());
                    } catch (Exception e) {
                        System.out.printf("  -> Cancel error: %s%n", e.getMessage());
                    }
                });
                cancelled++;
            }

            if (i < requests) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.printf("%nSummary: total=%d, successful=%d, failed=%d, cancel_attempts=%d%n",
                requests, successful, failed, cancelled);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TrafficGeneratorApp()).execute(args);
        System.exit(exitCode);
    }
}
