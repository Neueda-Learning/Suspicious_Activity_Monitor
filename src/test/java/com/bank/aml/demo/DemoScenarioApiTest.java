package com.bank.aml.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.aml.service.DemoResetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP contract for the demo console: status codes, step echoes on conflict, and the
 * monitoring guard that keeps a mid-scenario sweep from stealing the story.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoScenarioApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DemoResetService demoResetService;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        demoResetService.reset();
    }

    @Test
    @DisplayName("GET /api/demo/scenario reports READY after reset")
    void stateStartsReady() throws Exception {
        mockMvc.perform(get("/api/demo/scenario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("READY"));
    }

    @Test
    @DisplayName("golden path advances through the HTTP scenario endpoints")
    void httpGoldenPath() throws Exception {
        mockMvc.perform(post("/api/demo/scenario/payment-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PAYMENT_A_RELEASED"))
                .andExpect(jsonPath("$.anchorPayment.status").value("RELEASED"));

        mockMvc.perform(post("/api/demo/scenario/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("ACTIVITY_READY"))
                .andExpect(jsonPath("$.activity.transactionCount").value(8));

        mockMvc.perform(post("/api/demo/scenario/run-monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("CASE_RAISED"))
                .andExpect(jsonPath("$.raisedCase.priorityBand").value("RED"));

        mockMvc.perform(post("/api/demo/scenario/payment-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.step").value("PAYMENT_B_HELD"))
                .andExpect(jsonPath("$.sanctionsHold.status").value("HELD"));
    }

    @Test
    @DisplayName("out-of-order activity returns 409 with currentStep")
    void outOfOrderReturnsConflictWithStep() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/demo/scenario/activity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStep").value("READY"))
                .andExpect(jsonPath("$.error").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isNotBlank();
    }

    @Test
    @DisplayName("POST /api/monitoring/run is blocked while the scenario owns the window")
    void genericMonitoringBlockedMidScenario() throws Exception {
        mockMvc.perform(post("/api/demo/scenario/payment-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/monitoring/run"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStep").value("PAYMENT_A_RELEASED"));

        mockMvc.perform(post("/api/demo/scenario/activity")).andExpect(status().isOk());
        mockMvc.perform(post("/api/demo/scenario/run-monitoring")).andExpect(status().isOk());

        mockMvc.perform(post("/api/monitoring/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customersEvaluated").isNumber());
    }
}
