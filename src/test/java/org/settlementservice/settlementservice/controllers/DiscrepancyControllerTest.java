package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DiscrepancyController HTTP endpoints.
 * Business logic is tested in unit tests - these verify endpoint access and response structure.
 */
class DiscrepancyControllerTest extends AbstractControllerTest {

    @Test
    void search_withAuthentication_returnsPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/discrepancies")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").exists())
                .andExpect(jsonPath("$.data.page.totalPages").exists())
                .andExpect(jsonPath("$.data.page.size").value(20));
    }

    @Test
    void search_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/discrepancies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_withReconOfficer_returns200() throws Exception {
        mockMvc.perform(get("/api/discrepancies")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void search_withTransactionIdFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/discrepancies")
                        .param("transactionId", "123")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void search_withDefaultPageSize_uses20() throws Exception {
        mockMvc.perform(get("/api/discrepancies")
                        .param("page", "0")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.size").value(20));
    }

    @Test
    void search_withCustomPageSize_usesProvidedSize() throws Exception {
        mockMvc.perform(get("/api/discrepancies")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.size").value(5));
    }
}
