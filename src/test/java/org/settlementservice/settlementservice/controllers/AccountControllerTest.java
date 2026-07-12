package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.request.AccountUpdateRequest;
import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.enums.AccountStatus;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    private Long gtBankId;

    @BeforeEach
    void lookUpSeededBank() {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();
        gtBankId = gtBank.getId();
    }

    private AccountRequest accountRequest(String accountNumber) {
        AccountRequest request = new AccountRequest();
        request.setName("Test Corp");
        request.setAccountNumber(accountNumber);
        request.setBankId(gtBankId);
        return request;
    }

    @Test
    void create_asAdmin_validPayload_returns201WithBankIdAndName() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountNumber").value("1234567890"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.bankId").value(gtBankId))
                .andExpect(jsonPath("$.data.bankName").value("Guaranty Trust Bank"));
    }

    @Test
    void create_asReconOfficer_returns403() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(reconOfficerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_duplicateAccountNumber_returns409() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_invalidAccountNumberFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("not-numeric"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }

    @Test
    void create_nonexistentSettlementBank_returns404() throws Exception {
        AccountRequest request = accountRequest("1234567890");
        request.setBankId(999999L);

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_missingAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/accounts/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_changesStatusOnly() throws Exception {
        String createResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        AccountUpdateRequest updateRequest = new AccountUpdateRequest();
        updateRequest.setStatus(AccountStatus.INACTIVE);

        mockMvc.perform(put("/api/accounts/" + accountId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accountNumber").value("1234567890"));
    }

    @Test
    void delete_existingAccount_thenGetByIdReturns404() throws Exception {
        String createResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/accounts/" + accountId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted successfully"));

        mockMvc.perform(get("/api/accounts/" + accountId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    // Runs each service call in its own real, committed transaction instead of the class-level
    // @Transactional rollback wrapper — otherwise the classification-rule insert and the account
    // delete both stay as unflushed, uncommitted operations sharing one open transaction, and MySQL
    // never actually gets to evaluate the FK constraint mid-test. Cleans up manually since nothing
    // auto-rolls-back here.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void delete_accountWithDependentClassificationRule_returns409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(accountRequest("1234567890"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        try {
            ClassificationRuleRequest ruleRequest = new ClassificationRuleRequest();
            ruleRequest.setRegexPattern(".*FX.*");
            ruleRequest.setProductType(ProductType.TRANSFER);
            ruleRequest.setAccountId(accountId);
            String ruleResponse = mockMvc.perform(post("/api/classification-rules")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(asJson(ruleRequest)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            long ruleId = objectMapper.readTree(ruleResponse).get("data").get("id").asLong();

            mockMvc.perform(delete("/api/accounts/" + accountId).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This record cannot be modified because it is still referenced by other records"));

            mockMvc.perform(delete("/api/classification-rules/" + ruleId).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk());
        } finally {
            mockMvc.perform(delete("/api/accounts/" + accountId).header("Authorization", bearer(adminToken)));
        }
    }
}
