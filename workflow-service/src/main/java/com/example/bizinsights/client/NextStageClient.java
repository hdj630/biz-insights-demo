package com.example.bizinsights.client;

import com.example.bizinsights.model.StageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class NextStageClient {

    private static final Logger log = LoggerFactory.getLogger(NextStageClient.class);
    private final RestTemplate restTemplate;

    public NextStageClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public StageResponse callNext(String url, String orderId, String failAtStage) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Order-Id", orderId);
        if (failAtStage != null && !failAtStage.isEmpty()) {
            headers.set("X-Fail-At-Stage", failAtStage);
        }

        try {
            ResponseEntity<StageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    StageResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            log.error("Downstream call to {} failed: {}", url, e.getStatusCode());
            try {
                return e.getResponseBodyAs(StageResponse.class);
            } catch (Exception parseEx) {
                return StageResponse.failed(orderId, "downstream", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Downstream call to {} error: {}", url, e.getMessage());
            return StageResponse.failed(orderId, "downstream", e.getMessage());
        }
    }

    public StageResponse callCancel(String url, String orderId) {
        return callNext(url, orderId, null);
    }
}
