package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankStatementControllerTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    private Long accountId;

    @BeforeEach
    void createAccount() throws Exception {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();

        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setName("Upload Test Co");
        accountRequest.setAccountNumber("1234567890");
        accountRequest.setBankId(gtBank.getId());

        String response = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        accountId = objectMapper.readTree(response).get("data").get("id").asLong();
    }

    @Test
    void upload_newFile_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "row one\nrow two".getBytes());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Bank statement uploaded successfully"))
                .andExpect(jsonPath("$.data.fileName").value("statement1.csv"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void upload_sameFileTwiceForSameAccount_secondReturns409() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "same bytes".getBytes());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This file has already been uploaded for account " + accountId));
    }

    @Test
    void upload_reconOfficerCanUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(reconOfficerToken)))
                .andExpect(status().isCreated());
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "statement1.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(emptyFile)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_nonexistentAccount_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", "999999")
                        .param("openingBalance", "100000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", accountId.toString())
                        .param("openingBalance", "100000"))
                .andExpect(status().isUnauthorized());
    }
}
