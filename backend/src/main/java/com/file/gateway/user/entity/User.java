package com.file.gateway.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * User
 * - 시스템 사용자 정보를 저장하는 JPA 엔티티이다.
 * - 사용자명, BCrypt 암호화 비밀번호, 역할(ROLE_*), 계정 활성 상태, 리프레시 토큰을 관리한다.
 * - 역할(role)은 Spring Security의 GrantedAuthority와 매핑되어 접근 제어에 사용된다.
 * - 비밀번호 변경 필요 여부(passwordChanged)를 통해 최초 로그인 시 변경을 강제할 수 있다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 사용자 고유 식별자 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인에 사용되는 고유 사용자명 */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /** BCrypt로 암호화된 비밀번호 */
    @Column(name = "password", nullable = false)
    private String password;

    /** 사용자 역할 (예: ROLE_ADMIN, ROLE_AUDITOR, ROLE_END_USER) */
    @Column(name = "role", nullable = false)
    private String role;

    /** 계정 활성화 여부 (false이면 로그인 불가) */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 비밀번호 변경 완료 여부 (false이면 최초 로그인 후 비밀번호 변경 필요) */
    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged = true;

    /** JWT 리프레시 토큰 (로그아웃 시 null로 초기화) */
    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    /** 레코드 생성 시각 (자동 설정, 변경 불가) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 레코드 최종 수정 시각 (자동 갱신) */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * User 빌더 생성자.
     * role이 null이면 ROLE_END_USER로 기본값을 설정한다.
     * 빌더로 생성된 계정은 passwordChanged를 true로 초기화한다(시드 계정 용도).
     *
     * @param username 사용자명
     * @param password BCrypt 암호화된 비밀번호
     * @param role     사용자 역할 (null이면 ROLE_END_USER)
     */
    @Builder
    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role != null ? role : "ROLE_END_USER";
        this.active = true;
        this.passwordChanged = true;  // 빌더로 생성된 계정은 기본 true (시드 계정)
    }

    /**
     * JWT 리프레시 토큰을 갱신한다.
     *
     * @param token 저장할 리프레시 토큰 문자열 (로그아웃 시 null 전달)
     */
    public void updateRefreshToken(String token) {
        this.refreshToken = token;
    }

    /**
     * 계정을 비활성화하여 로그인을 차단한다.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * 비활성화된 계정을 다시 활성화한다.
     */
    public void activate() {
        this.active = true;
    }

    /**
     * 사용자 역할을 변경한다.
     *
     * @param role 변경할 역할 문자열 (예: ROLE_ADMIN)
     */
    public void updateRole(String role) {
        this.role = role;
    }

    /**
     * 비밀번호 변경이 필요한 상태로 표시한다. 다음 로그인 시 비밀번호 변경을 유도한다.
     */
    public void requirePasswordChange() {
        this.passwordChanged = false;
    }

    /**
     * 새 비밀번호로 변경하고 비밀번호 변경 완료 상태로 표시한다.
     *
     * @param encodedPassword BCrypt로 암호화된 새 비밀번호
     */
    public void markPasswordChanged(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChanged = true;
    }
}
