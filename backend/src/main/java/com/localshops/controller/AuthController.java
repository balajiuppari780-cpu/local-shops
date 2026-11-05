package com.localshops.controller;

import com.localshops.dto.AuthResponse;
import com.localshops.dto.LoginRequest;
import com.localshops.dto.SignupRequest;
import com.localshops.dto.UserResponse;
import com.localshops.exception.InvalidCredentialsException;
import com.localshops.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Creates a customer or shop owner account")
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Authenticate user", description = "Authenticates credentials and returns a JWT access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", description = "Returns profile of user identified by JWT token")
    public UserResponse me(Principal principal) {
        if (principal == null) {
            throw new InvalidCredentialsException("Authentication required");
        }
        return authService.getCurrentUser(principal.getName());
    }
}

