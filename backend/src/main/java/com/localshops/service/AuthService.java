package com.localshops.service;

import com.localshops.dto.AuthResponse;
import com.localshops.dto.LoginRequest;
import com.localshops.dto.SignupRequest;
import com.localshops.dto.UserResponse;
import com.localshops.exception.BadRequestException;
import com.localshops.exception.EmailAlreadyExistsException;
import com.localshops.exception.InvalidCredentialsException;
import com.localshops.model.Role;
import com.localshops.model.User;
import com.localshops.repository.UserRepository;
import com.localshops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }

        if (request.getRole() == Role.ADMIN) {
            throw new BadRequestException("ADMIN role cannot be assigned through public registration");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(UserResponse.fromEntity(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("User not found with email: " + email));
        return UserResponse.fromEntity(user);
    }
}
