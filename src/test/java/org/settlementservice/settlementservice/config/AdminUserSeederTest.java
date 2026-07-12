package org.settlementservice.settlementservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.settlementservice.settlementservice.enums.Role;
import org.settlementservice.settlementservice.models.User;
import org.settlementservice.settlementservice.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserSeeder adminUserSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminUserSeeder, "admin_username", "admin");
        ReflectionTestUtils.setField(adminUserSeeder, "admin_password", "admin123");
    }

    @Test
    void run_noExistingAdmin_createsOneWithHashedPasswordAndAdminRole() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin123")).thenReturn("hashed-password");

        adminUserSeeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void run_adminAlreadyExists_doesNotCreateAnotherOne() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User()));

        adminUserSeeder.run(null);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
