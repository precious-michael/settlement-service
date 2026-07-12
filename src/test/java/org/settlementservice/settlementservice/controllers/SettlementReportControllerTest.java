package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.enums.TransactionStatus;
import org.settlementservice.settlementservice.models.Account;
import org.settlementservice.settlementservice.models.BankStatement;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.models.Transaction;
import org.settlementservice.settlementservice.repositories.AccountRepository;
import org.settlementservice.settlementservice.repositories.BankStatementRepository;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.settlementservice.settlementservice.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementReportControllerTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankStatementRepository bankStatementRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Long transactionId;

    @BeforeEach
    void createTransactionToSettle() throws Exception {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();

        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setName("Report Test Co");
        accountRequest.setAccountNumber("1234567890");
        accountRequest.setBankId(gtBank.getId());
        String accountResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(accountResponse).get("data").get("id").asLong();
        Account account = accountRepository.findById(accountId).orElseThrow();

        MockMultipartFile file = new MockMultipartFile("file", "statement1.csv", "text/csv", "row".getBytes());
        String uploadResponse = mockMvc.perform(multipart("/api/bank-statements/upload")
                        .file(file)
                        .param("accountId", String.valueOf(accountId))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bankStatementId = objectMapper.readTree(uploadResponse).get("data").get("id").asLong();
        BankStatement bankStatement = bankStatementRepository.findById(bankStatementId).orElseThrow();

        Transaction transaction = new Transaction();
        transaction.setBankStatement(bankStatement);
        transaction.setAccount(account);
        transaction.setTransactionDate(LocalDate.now());
        transaction.setNarration("TEST TRANSFER");
        transaction.setReferenceNumber("REF-001");
        transaction.setDebit(BigDecimal.ZERO);
        transaction.setCredit(new BigDecimal("1000.00"));
        transaction.setStatus(TransactionStatus.UNRESOLVED);
        transaction = transactionRepository.save(transaction);
        transactionId = transaction.getId();
    }

    @Test
    void upload_newReport_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report1.csv", "text/csv", "row".getBytes());

        mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Settlement report uploaded successfully"))
                .andExpect(jsonPath("$.data.fileName").value("report1.csv"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void upload_secondReportForSameTransaction_returns409() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report1.csv", "text/csv", "row".getBytes());

        mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void upload_nonexistentTransaction_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report1.csv", "text/csv", "row".getBytes());

        mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(file)
                        .param("transactionId", "999999")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "report1.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/settlement-reports/upload")
                        .file(emptyFile)
                        .param("transactionId", transactionId.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }
}
