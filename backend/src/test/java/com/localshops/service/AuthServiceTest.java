package com.localshops.service;

import com.localshops.dto.SignupRequest;
import com.localshops.dto.UserResponse;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.EmailAlreadyExistsException;
import com.localshops.model.Role;
import com.localshops.model.User;
import com.localshops.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Should successfully register a new user with hashed password")
    void shouldRegisterNewUser() {
        SignupRequest request = SignupRequest.builder()
                .name("Kiran Reddy")
                .email("Kiran@example.com")
                .password("secret123")
                .role(Role.CUSTOMER)
                .build();

        when(userRepository.existsByEmail("kiran@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashedPasswordSample");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            userToSave.setId(1L);
            userToSave.setCreatedAt(Instant.now());
            return userToSave;
        });

        UserResponse response = authService.signup(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("kiran@example.com");
        assertThat(response.getName()).isEqualTo("Kiran Reddy");
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$hashedPasswordSample");
    }

    @Test
    @DisplayName("Should throw EmailAlreadyExistsException when email is taken")
    void shouldThrowWhenEmailAlreadyExists() {
        SignupRequest request = SignupRequest.builder()
                .name("Kiran Reddy")
                .email("kiran@example.com")
                .password("secret123")
                .role(Role.CUSTOMER)
                .build();

        when(userRepository.existsByEmail("kiran@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email is already registered");
    }

    @Test
    @DisplayName("Should throw BadRequestException when user tries to signup as ADMIN")
    void shouldThrowWhenSigningUpAsAdmin() {
        SignupRequest request = SignupRequest.builder()
                .name("Super User")
                .email("admin@example.com")
                .password("secret123")
                .role(Role.ADMIN)
                .build();

        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ADMIN role cannot be assigned through public registration");
    }
}

