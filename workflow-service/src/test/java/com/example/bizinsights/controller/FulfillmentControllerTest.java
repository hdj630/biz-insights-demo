package com.example.bizinsights.controller;

import com.example.bizinsights.client.NextStageClient;
import com.example.bizinsights.model.StageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StageController.class)
@TestPropertySource(properties = {
        "stage.name=fulfillment",
        "stage.next-url=",
        "stage.cancel-url=http://localhost:9999/process"
})
class FulfillmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NextStageClient nextStageClient;

    @Test
    void cancelTriggersRefund() throws Exception {
        when(nextStageClient.callCancel(eq("http://localhost:9999/process"), eq("order-10")))
                .thenReturn(StageResponse.completed("order-10", "refund", null));

        mockMvc.perform(post("/cancel")
                        .header("X-Order-Id", "order-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-10"))
                .andExpect(jsonPath("$.stage").value("fulfillment"))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.downstream.stage").value("refund"));
    }

    @Test
    void processTerminalStageReturnsCompleted() throws Exception {
        mockMvc.perform(post("/process")
                        .header("X-Order-Id", "order-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("fulfillment"))
                .andExpect(jsonPath("$.status").value("completed"));
    }
}
