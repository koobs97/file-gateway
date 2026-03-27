package com.file.gateway.auth;

import com.file.gateway.user.entity.User;
import com.file.gateway.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CustomUserDetailsService
 * - Spring Security의 UserDetailsService를 구현한 사용자 정보 로드 서비스
 * - 사용자명으로 DB를 조회하여 UserPrincipal 형태로 반환
 * - JwtAuthenticationFilter에서 토큰 인증 시 최신 사용자 정보를 재조회하는 데 사용
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** 사용자명으로 사용자 엔티티를 조회하는 Repository */
    private final UserRepository userRepository;

    /**
     * 사용자명으로 DB에서 사용자를 조회하여 UserDetails를 반환
     *
     * @param username 조회할 사용자명 (null 불허)
     * @return 조회된 사용자 정보를 담은 UserPrincipal 객체
     * @throws UsernameNotFoundException 해당 사용자명의 사용자가 존재하지 않는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
        return UserPrincipal.from(user);
    }
}
