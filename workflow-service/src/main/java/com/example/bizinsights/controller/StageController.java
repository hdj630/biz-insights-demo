package com.example.bizinsights.controller;

import com.example.bizinsights.client.NextStageClient;
import com.example.bizinsights.model.StageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StageController {

    private static final Logger log = LoggerFactory.getLogger(StageController.class);

    private final String stageName;
    private final String nextStageUrl;
    private final String cancelStageUrl;
    private final NextStageClient nextStageClient;

    public StageController(
            @Value("${stage.name}") String stageName,
            @Value("${stage.next-url:}") String nextStageUrl,
            @Value("${stage.cancel-url:}") String cancelStageUrl,
            NextStageClient nextStageClient) {
        this.stageName = stageName;
        this.nextStageUrl = nextStageUrl;
        this.cancelStageUrl = cancelStageUrl;
        this.nextStageClient = nextStageClient;
    }

    @PostMapping("/process")
    public ResponseEntity<StageResponse> process(
            @RequestHeader("X-Order-Id") String orderId,
            @RequestHeader(value = "X-Fail-At-Stage", required = false) String failAtStage) {

        log.info("[{}] Processing order: {}", stageName, orderId);

        if (stageName.equals(failAtStage)) {
            log.warn("[{}] Injected failure for order: {}", stageName, orderId);
            return ResponseEntity.internalServerError()
                    .body(StageResponse.failed(orderId, stageName, "Injected failure at " + stageName));
        }

        if (nextStageUrl != null && !nextStageUrl.isEmpty()) {
            StageResponse downstream = nextStageClient.callNext(nextStageUrl, orderId, failAtStage);
            if ("failed".equals(downstream.status())) {
                return ResponseEntity.internalServerError()
                        .body(StageResponse.failed(orderId, stageName,
                                "Downstream failure at " + downstream.stage()));
            }
            return ResponseEntity.ok(StageResponse.completed(orderId, stageName, downstream));
        }

        return ResponseEntity.ok(StageResponse.completed(orderId, stageName, null));
    }

    @PostMapping("/cancel")
    public ResponseEntity<StageResponse> cancel(
            @RequestHeader("X-Order-Id") String orderId) {

        if (!"fulfillment".equals(stageName)) {
            return ResponseEntity.badRequest()
                    .body(StageResponse.failed(orderId, stageName,
                            "Cancel is only supported on the fulfillment stage"));
        }

        log.info("[{}] Cancelling order: {}", stageName, orderId);

        if (cancelStageUrl != null && !cancelStageUrl.isEmpty()) {
            StageResponse refundResponse = nextStageClient.callCancel(cancelStageUrl, orderId);
            return ResponseEntity.ok(StageResponse.cancelled(orderId, stageName, refundResponse));
        }

        return ResponseEntity.ok(StageResponse.cancelled(orderId, stageName, null));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
