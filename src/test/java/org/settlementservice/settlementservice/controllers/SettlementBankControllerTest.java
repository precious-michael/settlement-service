package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.request.SettlementBankRequest;
import org.settlementservice.settlementservice.dtos.request.SettlementBankUpdateRequest;
import org.settlementservice.settlementservice.enums.SettlementBankStatus;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementBankControllerTest extends AbstractControllerTest {

    private SettlementBankRequest bankRequest(String name, String code) {
        SettlementBankRequest request = new SettlementBankRequest();
        request.setName(name);
        request.setCode(code);
        return request;
    }

    @Test
    void create_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Kuda Bank", "050"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Kuda Bank"))
                .andExpect(jsonPath("$.data.code").value("050"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void create_asReconOfficer_returns403() throws Exception {
        mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(reconOfficerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Kuda Bank", "050"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Guaranty Trust Bank", "999"))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_duplicateCode_returns409() throws Exception {
        mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Fake GTBank", "058"))))
                .andExpect(status().isConflict());
    }

    @Test
    void create_invalidCodeFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Kuda Bank", "AB1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_includesSeededBanks() throws Exception {
        mockMvc.perform(get("/api/settlement-banks").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='058')].name").value("Guaranty Trust Bank"));
    }

    @Test
    void update_changesNameAndStatus() throws Exception {
        String createResponse = mockMvc.perform(post("/api/settlement-banks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(bankRequest("Kuda Bank", "050"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bankId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        SettlementBankUpdateRequest updateRequest = new SettlementBankUpdateRequest();
        updateRequest.setName("Kuda Microfinance Bank");
        updateRequest.setStatus(SettlementBankStatus.INACTIVE);

        mockMvc.perform(put("/api/settlement-banks/" + bankId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJson(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Kuda Microfinance Bank"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.code").value("050"));
    }

    @Test
    void getById_missingBank_returns404() throws Exception {
        mockMvc.perform(get("/api/settlement-banks/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
