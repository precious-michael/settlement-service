package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.repositories.InternalRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for InternalRecordController.
 * Tests CRUD operations for internal records (used for testing/demo purposes).
 */
class InternalRecordControllerTest extends AbstractControllerTest {

    @Autowired
    private InternalRecordRepository internalRecordRepository;

    @Test
    void create_withValidRequest_createsInternalRecord() throws Exception {
        // Given: valid internal record request
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-001",
                    "rrn": "RRN-001",
                    "stan": "STAN-001",
                    "terminalId": "TERM-001",
                    "transactionDate": "2026-08-14",
                    "transactionTime": "10:30:00",
                    "narration": "Test internal record",
                    "debit": 0,
                    "credit": 500.00,
                    "currency": "NGN",
                    "status": "SUCCESSFUL"
                }
                """;

        // When: create internal record
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceNumber").value("TEST-REF-001"))
                .andExpect(jsonPath("$.data.rrn").value("RRN-001"))
                .andExpect(jsonPath("$.data.stan").value("STAN-001"))
                .andExpect(jsonPath("$.data.credit").value(500.00))
                .andExpect(jsonPath("$.data.debit").value(0))
                .andExpect(jsonPath("$.data.amount").value(500.00))
                .andExpect(jsonPath("$.data.currency").value("NGN"))
                .andExpect(jsonPath("$.data.status").value("SUCCESSFUL"));

        // Then: record is persisted
        assertThat(internalRecordRepository.findAll()).hasSize(1);
    }

    @Test
    void create_withMinimalFields_usesDefaults() throws Exception {
        // Given: minimal request (no time, currency, status)
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-002",
                    "rrn": "RRN-002",
                    "stan": "STAN-002",
                    "transactionDate": "2026-08-14",
                    "narration": "Minimal record",
                    "debit": 100.00,
                    "credit": 0
                }
                """;

        // When: create internal record
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("NGN"))
                .andExpect(jsonPath("$.data.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.data.transactionTime").exists());

        // Then: defaults are applied
        var record = internalRecordRepository.findAll().get(0);
        assertThat(record.getCurrency()).isEqualTo("NGN");
        assertThat(record.getStatus()).isEqualTo("SUCCESSFUL");
        assertThat(record.getTransactionTime()).isNotNull();
    }

    @Test
    void create_calculatesAmountCorrectly() throws Exception {
        // Given: credit transaction
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-003",
                    "rrn": "RRN-003",
                    "stan": "STAN-003",
                    "transactionDate": "2026-08-14",
                    "narration": "Amount calculation test",
                    "debit": 0,
                    "credit": 750.50
                }
                """;

        // When: create
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(750.50));
    }

    @Test
    void getAll_returnsAllInternalRecords() throws Exception {
        // Given: multiple internal records
        createInternalRecord("REF-100", "100.00");
        createInternalRecord("REF-101", "200.00");
        createInternalRecord("REF-102", "300.00");

        // When: get all
        mockMvc.perform(get("/api/internal-records")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].referenceNumber").exists())
                .andExpect(jsonPath("$.data[1].referenceNumber").exists())
                .andExpect(jsonPath("$.data[2].referenceNumber").exists());
    }

    @Test
    void getAll_withNoRecords_returnsEmptyList() throws Exception {
        // Given: no records

        // When: get all
        mockMvc.perform(get("/api/internal-records")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deleteAll_removesAllInternalRecords() throws Exception {
        // Given: existing records
        createInternalRecord("REF-200", "100.00");
        createInternalRecord("REF-201", "200.00");
        assertThat(internalRecordRepository.findAll()).hasSize(2);

        // When: delete all
        mockMvc.perform(delete("/api/internal-records/all")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All internal records deleted"));

        // Then: all records removed
        assertThat(internalRecordRepository.findAll()).isEmpty();
    }

    @Test
    void deleteAll_whenNoRecords_succeeds() throws Exception {
        // Given: no records
        assertThat(internalRecordRepository.findAll()).isEmpty();

        // When: delete all
        mockMvc.perform(delete("/api/internal-records/all")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        // Then: still no records
        assertThat(internalRecordRepository.findAll()).isEmpty();
    }

    @Test
    void create_withDebitTransaction_calculatesAmount() throws Exception {
        // Given: debit transaction
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-004",
                    "rrn": "RRN-004",
                    "stan": "STAN-004",
                    "transactionDate": "2026-08-14",
                    "narration": "Debit test",
                    "debit": 125.75,
                    "credit": 0
                }
                """;

        // When: create
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.debit").value(125.75))
                .andExpect(jsonPath("$.data.credit").value(0))
                .andExpect(jsonPath("$.data.amount").value(125.75));
    }

    @Test
    void create_withBothDebitAndCredit_calculatesAbsoluteDifference() throws Exception {
        // Given: transaction with both debit and credit (edge case)
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-005",
                    "rrn": "RRN-005",
                    "stan": "STAN-005",
                    "transactionDate": "2026-08-14",
                    "narration": "Both debit and credit",
                    "debit": 50.00,
                    "credit": 100.00
                }
                """;

        // When: create
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(50.00)); // abs(100 - 50)
    }

    @Test
    void create_withCustomCurrency_persistsCorrectly() throws Exception {
        // Given: USD transaction
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-006",
                    "rrn": "RRN-006",
                    "stan": "STAN-006",
                    "transactionDate": "2026-08-14",
                    "narration": "USD transaction",
                    "debit": 0,
                    "credit": 100.00,
                    "currency": "USD"
                }
                """;

        // When: create
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void endpoints_requireAuthentication() throws Exception {
        // Create
        mockMvc.perform(post("/api/internal-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Get all
        mockMvc.perform(get("/api/internal-records"))
                .andExpect(status().isUnauthorized());

        // Delete all
        mockMvc.perform(delete("/api/internal-records/all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpoints_allowAllAuthenticatedUsers() throws Exception {
        // Given: valid request
        String requestBody = """
                {
                    "referenceNumber": "TEST-REF-007",
                    "rrn": "RRN-007",
                    "stan": "STAN-007",
                    "transactionDate": "2026-08-14",
                    "narration": "Auth test",
                    "debit": 0,
                    "credit": 100.00
                }
                """;

        // When: recon officer creates
        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(reconOfficerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // And: recon officer can get all
        mockMvc.perform(get("/api/internal-records")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isOk());

        // And: recon officer can delete all
        mockMvc.perform(delete("/api/internal-records/all")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isOk());
    }

    // Helper method

    private void createInternalRecord(String referenceNumber, String amount) throws Exception {
        String requestBody = String.format("""
                {
                    "referenceNumber": "%s",
                    "rrn": "RRN-%s",
                    "stan": "STAN-%s",
                    "transactionDate": "2026-08-14",
                    "narration": "Test record %s",
                    "debit": 0,
                    "credit": %s
                }
                """, referenceNumber, referenceNumber, referenceNumber, referenceNumber, amount);

        mockMvc.perform(post("/api/internal-records")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }
}
