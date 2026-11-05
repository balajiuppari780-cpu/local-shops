package com.localshops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localshops.config.SecurityConfig;
import com.localshops.dto.AuthResponse;
import com.localshops.dto.LoginRequest;
import com.localshops.dto.SignupRequest;
import com.localshops.dto.UserResponse;
import com.localshops.exception.EmailAlreadyExistsException;
import com.localshops.exception.GlobalExceptionHandler;
import com.localshops.exception.InvalidCredentialsException;
import com.localshops.model.Role;
import com.localshops.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/signup - returns 201 Created on valid input")
    void shouldReturn201OnValidSignup() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .name("Ravi Teja")
                .email("ravi@example.com")
                .password("securePassword123")
                .role(Role.CUSTOMER)
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .name("Ravi Teja")
                .email("ravi@example.com")
                .role(Role.CUSTOMER)
                .createdAt(Instant.now())
                .build();

        when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Ravi Teja"))
                .andExpect(jsonPath("$.email").value("ravi@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - returns 400 Bad Request on invalid input")
    void shouldReturn400OnValidationFailure() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .name("") // blank name
                .email("not-an-email") // invalid email
                .password("123") // too short (< 6 chars)
                .role(null) // null role
                .build();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists())
                .andExpect(jsonPath("$.validationErrors.role").exists());
    }

    @Test
    @DisplayName("POST /api/auth/signup - returns 409 Conflict when email already exists")
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .name("Ravi Teja")
                .email("ravi@example.com")
                .password("securePassword123")
                .role(Role.CUSTOMER)
                .build();

        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email is already registered: ravi@example.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered: ravi@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - returns 200 OK with token on valid credentials")
    void shouldReturn200OnValidLogin() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("ravi@example.com")
                .password("securePassword123")
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("mocked.jwt.token")
                .tokenType("Bearer")
                .user(UserResponse.builder()
                        .id(1L)
                        .name("Ravi Teja")
                        .email("ravi@example.com")
                        .role(Role.CUSTOMER)
                        .build())
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("ravi@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - returns 400 Bad Request on invalid input")
    void shouldReturn400OnInvalidLoginInput() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("not-an-email")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - returns 401 Unauthorized on invalid credentials")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("ravi@example.com")
                .password("wrongpassword")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}

