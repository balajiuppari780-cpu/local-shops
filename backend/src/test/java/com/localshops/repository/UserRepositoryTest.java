package com.localshops.repository;

import com.localshops.model.Role;
import com.localshops.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should save user and find by email successfully")
    void shouldSaveAndFindByEmail() {
        User user = User.builder()
                .name("Ravi Kumar")
                .email("ravi@example.com")
                .passwordHash("hashed_secret_password")
                .role(Role.CUSTOMER)
                .build();

        User savedUser = userRepository.save(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();

        Optional<User> found = userRepository.findByEmail("ravi@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Ravi Kumar");
        assertThat(found.get().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("existsByEmail should return true for existing email and false for unknown email")
    void shouldCheckEmailExistence() {
        User user = User.builder()
                .name("Anita Sharma")
                .email("anita@example.com")
                .passwordHash("hashed_secret_password")
                .role(Role.SHOP_OWNER)
                .build();

        userRepository.save(user);

        assertThat(userRepository.existsByEmail("anita@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }
}
