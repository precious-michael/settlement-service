package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ReconciliationController HTTP endpoints.
 * Business logic is tested in unit tests - these verify endpoint access and response structure.
 */
class ReconciliationControllerTest extends AbstractControllerTest {

    @Test
    void run_withAuthentication_returns200AndStructuredResponse() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProcessed").exists())
                .andExpect(jsonPath("$.data.matched").exists())
                .andExpect(jsonPath("$.data.mismatched").exists())
                .andExpect(jsonPath("$.data.noMatchFound").exists());
    }

    @Test
    void run_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void run_withReconOfficer_returns200() throws Exception {
        mockMvc.perform(post("/api/reconciliation/run")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void results_withAuthentication_returnsPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/reconciliation/results")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").exists())
                .andExpect(jsonPath("$.data.page.totalPages").exists());
    }

    @Test
    void results_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/reconciliation/results"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void results_withTransactionIdFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/reconciliation/results")
                        .param("transactionId", "999")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
