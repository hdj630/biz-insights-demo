package com.example.bizinsights.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StageResponse(
        String orderId,
        String stage,
        String status,
        String error,
        StageResponse downstream
) {

    public static StageResponse completed(String orderId, String stage, StageResponse downstream) {
        return new StageResponse(orderId, stage, "completed", null, downstream);
    }

    public static StageResponse failed(String orderId, String stage, String error) {
        return new StageResponse(orderId, stage, "failed", error, null);
    }

    public static StageResponse cancelled(String orderId, String stage, StageResponse refundResponse) {
        return new StageResponse(orderId, stage, "cancelled", null, refundResponse);
    }
}
