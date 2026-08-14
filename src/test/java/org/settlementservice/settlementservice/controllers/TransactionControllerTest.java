package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TransactionController HTTP endpoints.
 * Business logic is tested in unit tests - these verify endpoint access and response structure.
 */
class TransactionControllerTest extends AbstractControllerTest {

    @Test
    void search_withAuthentication_returnsPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").exists())
                .andExpect(jsonPath("$.data.page.totalPages").exists());
    }

    @Test
    void search_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_withReconOfficer_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void search_withStatusFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("status", "UNRESOLVED")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void search_withAccountIdFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", "123")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void search_withProductTypeFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("productType", "CARD_SETTLEMENT")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void search_withDateRangeFilter_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("dateFrom", "2026-08-01")
                        .param("dateTo", "2026-08-14")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void search_withMultipleFilters_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("accountId", "123")
                        .param("status", "RESOLVED")
                        .param("productType", "PAYROLL")
                        .param("dateFrom", "2026-08-01")
                        .param("dateTo", "2026-08-14")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void search_withDefaultPageSize_uses20() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("page", "0")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.size").value(20));
    }
}
