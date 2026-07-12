package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.request.AccountRequest;
import org.settlementservice.settlementservice.dtos.request.ClassificationRuleRequest;
import org.settlementservice.settlementservice.enums.ProductType;
import org.settlementservice.settlementservice.models.SettlementBank;
import org.settlementservice.settlementservice.repositories.SettlementBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassificationRuleControllerTest extends AbstractControllerTest {

    @Autowired
    private SettlementBankRepository settlementBankRepository;

    private Long accountId;

    @BeforeEach
    void createAccountForScopedRules() throws Exception {
        SettlementBank gtBank = settlementBankRepository.findByCode("058").orElseThrow();

        AccountRequest accountRequest = new AccountRequest();
        accountRequest.setName("Test Corp");
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

    private ClassificationRuleRequest ruleRequest(String regex, ProductType productType, Long accountId) {
        ClassificationRuleRequest request = new ClassificationRuleRequest();
        request.setRegexPattern(regex);
        request.setProductType(productType);
        request.setAccountId(accountId);
        return request;
    }

    @Test
    void create_globalRule_withNullAccountId_returns201() throws Exception {
        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*CARD SETTLEMENT.*", ProductType.CARD_SETTLEMENT, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").doesNotExist());
    }

    @Test
    void create_accountScopedRule_returns201WithAccountId() throws Exception {
        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*FX.*", ProductType.TRANSFER, accountId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(accountId));
    }

    @Test
    void create_asReconOfficer_returns403() throws Exception {
        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(reconOfficerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*FX.*", ProductType.TRANSFER, accountId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_nonexistentAccountId_returns404() throws Exception {
        mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*FX.*", ProductType.TRANSFER, 999999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_changesRegexAndProductType() throws Exception {
        String createResponse = mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*OLD.*", ProductType.OTHERS, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ruleId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        mockMvc.perform(put("/api/classification-rules/" + ruleId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*NEW.*", ProductType.PAYROLL, accountId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regexPattern").value(".*NEW.*"))
                .andExpect(jsonPath("$.data.productType").value("PAYROLL"))
                .andExpect(jsonPath("$.data.accountId").value(accountId));
    }

    @Test
    void delete_existingRule_returns200() throws Exception {
        String createResponse = mockMvc.perform(post("/api/classification-rules")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(ruleRequest(".*TEMP.*", ProductType.OTHERS, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ruleId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/classification-rules/" + ruleId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Classification rule deleted successfully"));
    }
}
