package com.gateway.bootstrap;

import com.gateway.entity.Role;
import com.gateway.entity.User;
import com.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component; 
import java.util.Set;

/**
 * Seeds a demo admin and a demo user on startup so the API can be exercised
 * immediately without a manual /register call. Remove or gate behind a
 * profile before deploying to production.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_USER))
                    .enabled(true)
                    .build());
        }

        if (!userRepository.existsByUsername("demo")) {
            userRepository.save(User.builder()
                    .username("demo")
                    .email("demo@example.com")
                    .password(passwordEncoder.encode("Demo@123"))
                    .roles(Set.of(Role.ROLE_USER))
                    .enabled(true)
                    .build());
        }
    }
}
