package com.file.gateway.auth;

import com.file.gateway.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * UserPrincipal
 * - Spring Security의 UserDetails를 구현한 인증된 사용자 주체(Principal) 레코드
 * - User 엔티티로부터 변환되어 SecurityContext에 저장되는 불변 객체
 * - 역할(role), 활성화 상태(active), 비밀번호 변경 여부(passwordChanged) 정보를 포함
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record UserPrincipal(
        /** 사용자 고유 식별자 */
        Long id,
        /** 로그인에 사용되는 사용자명 */
        String username,
        /** 암호화된 비밀번호 */
        String password,
        /** 사용자 역할 (예: ROLE_ADMIN, ROLE_END_USER) */
        String role,
        /** 계정 활성화 여부 */
        boolean active,
        /** 최초 로그인 후 비밀번호 변경 완료 여부 */
        boolean passwordChanged,
        /** Spring Security 권한 목록 */
        List<GrantedAuthority> authorities
) implements UserDetails {

    /**
     * User 엔티티를 UserPrincipal 레코드로 변환
     *
     * @param user 변환할 사용자 엔티티 (null 불허)
     * @return 사용자 정보를 담은 UserPrincipal 인스턴스
     */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.isActive(),
                user.isPasswordChanged(),
                List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }

    /**
     * 사용자에게 부여된 Spring Security 권한 목록을 반환
     *
     * @return 역할 기반 GrantedAuthority 목록
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 암호화된 비밀번호를 반환
     *
     * @return 암호화된 비밀번호 문자열
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 사용자명을 반환
     *
     * @return 사용자명 문자열
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 계정 만료 여부를 반환 (항상 만료되지 않음)
     *
     * @return 항상 true
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 계정 잠금 여부를 반환 (항상 잠금되지 않음)
     *
     * @return 항상 true
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 자격 증명(비밀번호) 만료 여부를 반환 (항상 만료되지 않음)
     *
     * @return 항상 true
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 계정 활성화 여부를 반환
     *
     * @return active 필드 값 (true이면 활성, false이면 비활성)
     */
    @Override
    public boolean isEnabled() {
        return active;
    }
}
