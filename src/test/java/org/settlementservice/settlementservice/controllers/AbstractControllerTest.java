package org.settlementservice.settlementservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.settlementservice.settlementservice.TestcontainersConfiguration;
import org.settlementservice.settlementservice.enums.Role;
import org.settlementservice.settlementservice.models.User;
import org.settlementservice.settlementservice.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String adminToken;
    protected String reconOfficerToken;

    @BeforeEach
    void authenticateAsAdminAndReconOfficer() throws Exception {
        // Seeded on startup by AdminUserSeeder — always present regardless of what a given test creates.
        adminToken = obtainAccessToken("admin", "password");

        String officerUsername = "officer-" + UUID.randomUUID();
        User officer = new User();
        officer.setUsername(officerUsername);
        officer.setPasswordHash(passwordEncoder.encode("officer123"));
        officer.setRole(Role.RECON_OFFICER);
        userRepository.save(officer);

        reconOfficerToken = obtainAccessToken(officerUsername, "officer123");
    }

    protected String obtainAccessToken(String username, String password) throws Exception {
        String requestBody = objectMapper.writeValueAsString(new LoginPayload(username, password));

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("data").get("access_token").asString();
    }

    protected String asJson(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginPayload(String username, String password) {
    }
}
