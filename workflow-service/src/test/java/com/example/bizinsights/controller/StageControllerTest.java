package com.example.bizinsights.controller;

import com.example.bizinsights.client.NextStageClient;
import com.example.bizinsights.model.StageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StageController.class)
@TestPropertySource(properties = {
        "stage.name=payment",
        "stage.next-url=http://localhost:9999/process",
        "stage.cancel-url="
})
class StageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NextStageClient nextStageClient;

    @Test
    void processCompletesSuccessfully() throws Exception {
        when(nextStageClient.callNext(anyString(), eq("order-1"), eq(null)))
                .thenReturn(StageResponse.completed("order-1", "fulfillment", null));

        mockMvc.perform(post("/process")
                        .header("X-Order-Id", "order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.stage").value("payment"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.downstream.stage").value("fulfillment"));
    }

    @Test
    void processFailsWhenFailAtStageMatchesThisStage() throws Exception {
        mockMvc.perform(post("/process")
                        .header("X-Order-Id", "order-2")
                        .header("X-Fail-At-Stage", "payment"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.orderId").value("order-2"))
                .andExpect(jsonPath("$.stage").value("payment"))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.error").value("Injected failure at payment"));
    }

    @Test
    void processForwardsWhenFailAtStageDoesNotMatch() throws Exception {
        when(nextStageClient.callNext(anyString(), eq("order-3"), eq("browse")))
                .thenReturn(StageResponse.completed("order-3", "fulfillment", null));

        mockMvc.perform(post("/process")
                        .header("X-Order-Id", "order-3")
                        .header("X-Fail-At-Stage", "browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void processReturnsFailureWhenDownstreamFails() throws Exception {
        when(nextStageClient.callNext(anyString(), eq("order-4"), eq("fulfillment")))
                .thenReturn(StageResponse.failed("order-4", "fulfillment", "Injected failure"));

        mockMvc.perform(post("/process")
                        .header("X-Order-Id", "order-4")
                        .header("X-Fail-At-Stage", "fulfillment"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.error").value("Downstream failure at fulfillment"));
    }

    @Test
    void cancelRejectedForNonFulfillmentStage() throws Exception {
        mockMvc.perform(post("/cancel")
                        .header("X-Order-Id", "order-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cancel is only supported on the fulfillment stage"));
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
