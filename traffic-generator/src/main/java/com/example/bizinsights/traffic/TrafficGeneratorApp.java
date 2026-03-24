package com.example.bizinsights.traffic;

import com.example.bizinsights.traffic.client.WorkflowClient;
import com.example.bizinsights.traffic.store.OrderIdStore;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(name = "traffic-generator", mixinStandardHelpOptions = true,
        description = "Generates traffic for the business insights demo workflow")
public class TrafficGeneratorApp implements Callable<Integer> {

    @Option(names = "--requests", defaultValue = "0",
            description = "Number of requests (batch mode). 0 = use continuous mode with --tps")
    private int requests;

    @Option(names = "--delay", defaultValue = "2000",
            description = "Delay between requests in ms (batch mode only)")
    private long delayMs;

    @Option(names = "--tps", defaultValue = "0",
            description = "Target transactions per second (continuous mode). Overrides --requests/--delay")
    private double tps;

    @Option(names = "--duration", defaultValue = "0",
            description = "Duration in seconds for continuous mode. 0 = run until Ctrl+C")
    private long durationSeconds;

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
        if (tps > 0) {
            return runContinuous();
        }
        if (requests <= 0) {
            System.err.println("Specify either --tps for continuous mode or --requests for batch mode");
            return 1;
        }
        return runBatch();
    }

    private int runContinuous() {
        long intervalMicros = (long) (1_000_000.0 / tps);
        String durationStr = durationSeconds > 0 ? durationSeconds + "s" : "until Ctrl+C";

        System.out.printf("Continuous mode: tps=%.1f, duration=%s, failAt=%s, cancelRatio=%.1f%n",
                tps, durationStr, failAtStage.isEmpty() ? "none" : failAtStage, cancelRatio);

        AtomicInteger successful = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger cancelAttempts = new AtomicInteger();
        AtomicInteger total = new AtomicInteger();
        AtomicBoolean running = new AtomicBoolean(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            printSummary(total.get(), successful.get(), failed.get(), cancelAttempts.get());
        }));

        ScheduledExecutorService statsPrinter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-printer");
            t.setDaemon(true);
            return t;
        });
        statsPrinter.scheduleAtFixedRate(() -> {
            System.out.printf("[stats] total=%d, successful=%d, failed=%d, cancelled=%d, stored_orders=%d%n",
                    total.get(), successful.get(), failed.get(), cancelAttempts.get(), store.size());
        }, 10, 10, TimeUnit.SECONDS);

        int threadCount = Math.max(1, (int) Math.ceil(tps / 50.0));
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "traffic-worker");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            int i = total.incrementAndGet();
            sendRequest(i, successful, failed, cancelAttempts);
        }, 0, intervalMicros, TimeUnit.MICROSECONDS);

        try {
            if (durationSeconds > 0) {
                Thread.sleep(durationSeconds * 1000);
                running.set(false);
            } else {
                while (running.get()) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdownNow();
        statsPrinter.shutdownNow();
        printSummary(total.get(), successful.get(), failed.get(), cancelAttempts.get());
        return 0;
    }

    private int runBatch() {
        System.out.printf("Batch mode: %d requests, delay=%dms, failAt=%s, cancelRatio=%.1f%n",
                requests, delayMs, failAtStage.isEmpty() ? "none" : failAtStage, cancelRatio);

        AtomicInteger successful = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger cancelAttempts = new AtomicInteger();

        for (int i = 1; i <= requests; i++) {
            sendRequest(i, successful, failed, cancelAttempts);

            if (i < requests) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        printSummary(requests, successful.get(), failed.get(), cancelAttempts.get());
        return 0;
    }

    private void sendRequest(int index, AtomicInteger successful, AtomicInteger failed,
                             AtomicInteger cancelAttempts) {
        String orderId = UUID.randomUUID().toString();

        try {
            HttpResponse<String> response = client.startWorkflow(browseUrl, orderId, failAtStage);

            if (response.statusCode() == 200) {
                successful.incrementAndGet();
                store.add(orderId);
            } else {
                failed.incrementAndGet();
                System.out.printf("[%d] Failed (HTTP %d) order=%s%n", index, response.statusCode(), orderId);
            }
        } catch (Exception e) {
            failed.incrementAndGet();
            System.out.printf("[%d] Error order=%s: %s%n", index, orderId, e.getMessage());
        }

        if (cancelRatio > 0 && ThreadLocalRandom.current().nextDouble() < cancelRatio) {
            store.pickRandom().ifPresent(cancelOrderId -> {
                try {
                    client.cancelOrder(fulfillmentCancelUrl, cancelOrderId);
                } catch (Exception e) {
                    System.out.printf("[%d] Cancel error: %s%n", index, e.getMessage());
                }
            });
            cancelAttempts.incrementAndGet();
        }
    }

    private void printSummary(int total, int successful, int failed, int cancelled) {
        System.out.printf("%nSummary: total=%d, successful=%d, failed=%d, cancel_attempts=%d%n",
                total, successful, failed, cancelled);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new TrafficGeneratorApp()).execute(args);
        System.exit(exitCode);
    }
}
