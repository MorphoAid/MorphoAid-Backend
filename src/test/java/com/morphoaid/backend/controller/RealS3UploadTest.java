package com.morphoaid.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RealS3UploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    public void testRealS3Upload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "real-test.png", MediaType.IMAGE_PNG_VALUE,
                "dummy image content for real s3 test".getBytes());

        mockMvc.perform(multipart("/cases/1/images")
                .file(file))
                .andExpect(status().isOk());
    }
}
