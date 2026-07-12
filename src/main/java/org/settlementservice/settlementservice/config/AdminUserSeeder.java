package org.settlementservice.settlementservice.config;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.enums.Role;
import org.settlementservice.settlementservice.models.User;
import org.settlementservice.settlementservice.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures at least one ADMIN user exists so the app is usable on a fresh database,
 * without hardcoding a password hash into a migration.
 */
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed-username}")
    private String admin_username;
    @Value("${admin.seed-password}")
    private String admin_password;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(admin_username).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setUsername(admin_username);
        admin.setPasswordHash(passwordEncoder.encode(admin_password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.warn("Seeded default admin user '{}' — change its password before any real deployment.", admin_username);
    }
}
