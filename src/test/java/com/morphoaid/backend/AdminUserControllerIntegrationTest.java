package com.morphoaid.backend;

import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.morphoaid.backend.repository.CaseRepository caseRepository;

    @Autowired
    private com.morphoaid.backend.repository.AIResultRepository aiResultRepository;

    @BeforeEach
    void setUp() {
        aiResultRepository.deleteAll();
        caseRepository.deleteAll();
        userRepository.deleteAll();
        User admin = User.builder()
                .email("admin@test.com")
                .password("encoded_pass")
                .role(Role.ADMIN)
                .build();
        User dataUse = User.builder()
                .email("datause@test.com")
                .password("encoded_pass")
                .role(Role.DATA_USE)
                .build();
        userRepository.save(admin);
        userRepository.save(dataUse);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdmin_CanAccessUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "DATA_USE")
    void testDataUse_Forbidden() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden()); // Or 403 depending on filter setup, usually 403 if default auth
                                                    // checks fail
    }
}
