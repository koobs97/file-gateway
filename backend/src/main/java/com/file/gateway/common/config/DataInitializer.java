package com.file.gateway.common.config;

import com.file.gateway.user.entity.User;
import com.file.gateway.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
    }

    private void seedUsers() {
        record SeedUser(String username, String role) {}

        List<SeedUser> seeds = List.of(
                new SeedUser("admin", "ROLE_ADMIN"),
                new SeedUser("auditor", "ROLE_AUDITOR"),
                new SeedUser("user1", "ROLE_END_USER"),
                new SeedUser("user2", "ROLE_END_USER")
        );

        for (SeedUser seed : seeds) {
            if (userRepository.findByUsername(seed.username()).isEmpty()) {
                User user = User.builder()
                        .username(seed.username())
                        .password(passwordEncoder.encode("password123"))
                        .role(seed.role())
                        .build();
                userRepository.save(user);
                log.info("시드 계정 생성: {} ({})", seed.username(), seed.role());
            }
        }
    }
}
