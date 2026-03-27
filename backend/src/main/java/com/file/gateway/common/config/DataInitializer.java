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

/**
 * DataInitializer
 * - 애플리케이션 시작 시 시드(seed) 계정을 자동으로 생성하는 초기화 컴포넌트
 * - 이미 존재하는 계정은 중복 생성하지 않으며 멱등성을 보장
 * - 생성 대상: admin(ROLE_ADMIN), auditor(ROLE_AUDITOR), user1/user2(ROLE_END_USER)
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** 사용자 엔티티 저장소 */
    private final UserRepository userRepository;

    /** 비밀번호 암호화 인코더 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 애플리케이션 구동 완료 후 시드 데이터 초기화를 실행한다.
     *
     * @param args 애플리케이션 실행 인수
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
    }

    /**
     * 사전 정의된 시드 계정 목록을 순회하여 존재하지 않는 계정을 생성한다.
     * 초기 비밀번호는 {@code password123}으로 BCrypt 인코딩하여 저장한다.
     */
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
