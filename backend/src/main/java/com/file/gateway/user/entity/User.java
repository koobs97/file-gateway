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

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "password_changed", nullable = false)
    private boolean passwordChanged = true;

    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role != null ? role : "ROLE_END_USER";
        this.active = true;
        this.passwordChanged = true;  // 빌더로 생성된 계정은 기본 true (시드 계정)
    }

    public void updateRefreshToken(String token) {
        this.refreshToken = token;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void updateRole(String role) {
        this.role = role;
    }

    public void requirePasswordChange() {
        this.passwordChanged = false;
    }

    public void markPasswordChanged(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordChanged = true;
    }
}
