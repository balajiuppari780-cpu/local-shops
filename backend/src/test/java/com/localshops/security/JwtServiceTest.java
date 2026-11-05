package com.localshops.security;

import com.localshops.model.Role;
import com.localshops.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "testSecretKeyForJwtGenerationMustBeLongEnough1234567890";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Should generate valid JWT with subject and role claims")
    void shouldGenerateAndExtractClaims() {
        User user = User.builder()
                .id(10L)
                .name("Suresh")
                .email("suresh@example.com")
                .role(Role.SHOP_OWNER)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo("suresh@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("SHOP_OWNER");
        assertThat(jwtService.isTokenValid(token, "suresh@example.com")).isTrue();
    }

    @Test
    @DisplayName("Should invalidate token when email does not match")
    void shouldInvalidateOnEmailMismatch() {
        User user = User.builder()
                .id(1L)
                .email("user1@example.com")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, "other@example.com")).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void shouldDetectExpiredToken() {
        // Create service with -1000ms expiration to immediately expire
        JwtService expiredJwtService = new JwtService(TEST_SECRET, -1000L);

        User user = User.builder()
                .id(2L)
                .email("expired@example.com")
                .role(Role.CUSTOMER)
                .build();

        String token = expiredJwtService.generateToken(user);

        assertThat(jwtService.isTokenExpired(token)).isTrue();
        assertThat(jwtService.isTokenValid(token, "expired@example.com")).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered token")
    void shouldRejectTamperedToken() {
        User user = User.builder()
                .id(3L)
                .email("valid@example.com")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtService.isTokenValid(tamperedToken, "valid@example.com")).isFalse();
    }
}
