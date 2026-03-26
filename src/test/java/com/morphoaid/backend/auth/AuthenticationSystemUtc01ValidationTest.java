package com.morphoaid.backend.auth;

import com.morphoaid.backend.dto.RegisterDataUseRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthenticationSystemUtc01ValidationTest {

    private Validator validator;

    @BeforeEach
    public void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private RegisterDataUseRequest createValidRequest() {
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
    public void UTC_01_TC_02_register_withInvalidEmailFormat() {
        // UTC-01-TD-02
        RegisterDataUseRequest request = createValidRequest();
        request.setEmail("invalid-format");

        Set<ConstraintViolation<RegisterDataUseRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email") && v.getMessage().equals("Email must be valid"));
    }

    @Test
    public void UTC_01_TC_04_register_withMissingRequiredFields() {
        // UTC-01-TD-04
        RegisterDataUseRequest request = new RegisterDataUseRequest();
        // Missing firstName, lastName, email, password, confirmPassword
        request.setAgreeTerms(true);

        Set<ConstraintViolation<RegisterDataUseRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lastName"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    public void UTC_01_TC_05_register_withInvalidNamePattern() {
        // UTC-01-TD-05
        RegisterDataUseRequest request = createValidRequest();
        request.setFirstName("John123");
        request.setLastName("Doe#");

        Set<ConstraintViolation<RegisterDataUseRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("firstName") && v.getMessage().contains("alphabetic"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lastName") && v.getMessage().contains("alphabetic"));
    }

    @Test
    public void UTC_01_TC_06_register_withoutAcceptingTerms() {
        // UTC-01-TD-06
        RegisterDataUseRequest request = createValidRequest();
        request.setAgreeTerms(false);

        Set<ConstraintViolation<RegisterDataUseRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("agreeTerms") && v.getMessage().equals("You must agree to the terms"));
    }
}
