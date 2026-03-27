package com.file.gateway.user.repository;

import com.file.gateway.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * UserRepository
 * - User 엔티티에 대한 데이터 접근 계층이다.
 * - 사용자명 기반 조회 및 중복 확인 메서드를 제공한다.
 * - Spring Security 인증 처리 및 관리자 사용자 관리에서 사용된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 사용자명으로 사용자를 조회한다.
     *
     * @param username 조회할 사용자명
     * @return 해당 사용자명의 User Optional
     */
    Optional<User> findByUsername(String username);

    /**
     * 사용자명의 중복 여부를 확인한다.
     *
     * @param username 확인할 사용자명
     * @return 이미 존재하는 사용자명이면 true
     */
    boolean existsByUsername(String username);
}
