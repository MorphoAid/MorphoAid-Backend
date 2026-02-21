package com.morphoaid.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphoaid.backend.dto.DataPrepRegisterRequest;
import com.morphoaid.backend.dto.LoginRequest;
import com.morphoaid.backend.dto.RegisterRequest;
import com.morphoaid.backend.entity.InvitationToken;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.repository.InvitationTokenRepository;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationTokenRepository tokenRepository;

    @Autowired
    private com.morphoaid.backend.repository.CaseRepository caseRepository;

    @Autowired
    private com.morphoaid.backend.repository.AIResultRepository aiResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        aiResultRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();
        tokenRepository.deleteAll();
    }

    @Test
    void testPublicRegistration_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.user.role").value("DATA_USE"));
    }

    @Test
    void testDataPrepRegistration_Success() throws Exception {
        InvitationToken token = InvitationToken.builder()
                .token("VALID_TOKEN")
                .role(Role.DATA_PREP)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        tokenRepository.save(token);

        DataPrepRegisterRequest request = new DataPrepRegisterRequest();
        request.setEmail("dataprep@example.com");
        request.setPassword("password123");
        request.setInvitationToken("VALID_TOKEN");

        mockMvc.perform(post("/auth/register/dataprep")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.role").value("DATA_PREP"));
    }

    @Test
    void testDataPrepRegistration_InvalidToken() throws Exception {
        DataPrepRegisterRequest request = new DataPrepRegisterRequest();
        request.setEmail("dataprep@example.com");
        request.setPassword("password123");
        request.setInvitationToken("INVALID_TOKEN");

        // The service throws IllegalArgumentException which typically results in 500
        // without handled properly, we expect a failure
        // GlobalExceptionHandler might map it to 400 Bad Request
        mockMvc.perform(post("/auth/register/dataprep")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogin_Success() throws Exception {
        // Register first
        RegisterRequest request = new RegisterRequest();
        request.setEmail("loginuser@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("loginuser@example.com");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
