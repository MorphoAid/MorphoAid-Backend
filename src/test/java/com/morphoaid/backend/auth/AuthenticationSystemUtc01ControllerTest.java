package com.morphoaid.backend.auth;

import com.morphoaid.backend.controller.AuthController;
import com.morphoaid.backend.dto.AuthResponse;
import com.morphoaid.backend.dto.LoginRequest;
import com.morphoaid.backend.dto.RegisterDataUseRequest;
import com.morphoaid.backend.dto.UserSummary;
import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import com.morphoaid.backend.service.InvitationTokenService;
import com.morphoaid.backend.service.ActivityService;
import com.morphoaid.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AuthenticationSystemUtc01ControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private InvitationTokenService tokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private RegisterDataUseRequest createValidRegisterRequest() {
        RegisterDataUseRequest request = new RegisterDataUseRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("doc@test.com");
        request.setPassword("Password123");
        request.setConfirmPassword("Password123");
        request.setAgreeTerms(true);
        return request;
    }

    @Test
    public void UTC_01_TC_01_register_withValidData() {
        // UTC-01-TD-01
        RegisterDataUseRequest request = createValidRegisterRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        
        User savedUser = User.builder()
                .id(1L)
                .email(request.getEmail())
                .role(Role.DATA_USE)
                .approved(false)
                .build();
        when(userRepository.save(any())).thenReturn(savedUser);

        ResponseEntity<?> response = authController.register(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        AuthResponse authResponse = (AuthResponse) response.getBody();
        assertThat(authResponse.getUser().isApproved()).isFalse();
        verify(activityService).log(eq(request.getEmail()), eq(Role.DATA_USE), anyString(), anyString(), eq("Success"));
    }

    @Test
    public void UTC_01_TC_03_register_withPasswordMismatch() {
        // UTC-01-TD-03
        RegisterDataUseRequest request = createValidRegisterRequest();
        request.setConfirmPassword("Mismatch123");

        ResponseEntity<?> response = authController.register(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains("Passwords do not match");
    }

    @Test
    public void UTC_01_TC_07_register_withExistingEmail() {
        // UTC-01-TD-07
        RegisterDataUseRequest request = createValidRegisterRequest();
        request.setEmail("admin@morphoaid.com");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(new User()));

        ResponseEntity<?> response = authController.register(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().toString()).contains("Email already in use");
    }

    @Test
    public void UTC_01_TC_08_login_withValidCredentials() {
        // UTC-01-TD-08
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@morphoaid.com");
        loginRequest.setPassword("password");

        User user = User.builder().id(1L).email(loginRequest.getEmail()).password("encoded").role(Role.ADMIN).build();
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(loginRequest.getPassword(), "encoded")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("validToken");

        ResponseEntity<AuthResponse> response = authController.login(loginRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getAccessToken()).isEqualTo("validToken");
    }

    @Test
    public void UTC_01_TC_09_login_withWrongPassword() {
        // UTC-01-TD-09
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@morphoaid.com");
        loginRequest.setPassword("wrong");

        User user = User.builder().email(loginRequest.getEmail()).password("encoded").build();
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        ResponseEntity<AuthResponse> response = authController.login(loginRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    public void UTC_01_TC_10_login_withNonExistingEmail() {
        // UTC-01-TD-10
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("ghost@test.com");
        loginRequest.setPassword("any");

        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        ResponseEntity<AuthResponse> response = authController.login(loginRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    public void UTC_01_TC_11_checkEmail_Existing() {
        // UTC-01-TD-07 email
        String email = "admin@morphoaid.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(new User()));

        ResponseEntity<?> response = authController.checkEmail(email);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().toString()).contains("This email already exists.");
    }

    @Test
    public void UTC_01_TC_12_checkEmail_New() {
        // UTC-01-TD-12 email
        String email = "newuser@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.checkEmail(email);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    public void UTC_01_TC_13_retrieveCurrentUser_Success() {
        // UTC-01-TD-13 Principal
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("admin@morphoaid.com");
        
        User user = User.builder().id(1L).email("admin@morphoaid.com").firstName("Admin").lastName("User").role(Role.ADMIN).build();
        when(userRepository.findByEmail("admin@morphoaid.com")).thenReturn(Optional.of(user));

        ResponseEntity<UserSummary> response = authController.getCurrentUser(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getEmail()).isEqualTo("admin@morphoaid.com");
    }
}
