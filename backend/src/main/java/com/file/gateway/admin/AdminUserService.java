package com.file.gateway.admin;

import com.file.gateway.admin.dto.CreateUserRequest;
import com.file.gateway.admin.dto.UserSummaryResponse;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import com.file.gateway.user.entity.User;
import com.file.gateway.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminUserService
 * - 관리자 권한으로 수행하는 사용자 관리 비즈니스 로직 처리
 * - 사용자 생성 시 비밀번호 암호화 및 최초 비밀번호 변경 요구 설정
 * - 사용자 역할 및 활성화 상태 변경 기능 제공
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** 사용자 엔티티 조회 및 저장을 위한 Repository */
    private final UserRepository userRepository;

    /** 비밀번호 암호화를 위한 인코더 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 새 사용자를 생성하고 초기 비밀번호 변경 요구 플래그를 설정한 후 저장
     *
     * @param request 사용자명, 초기 비밀번호, 역할을 담은 생성 요청 DTO (null 불허)
     * @return 저장된 사용자 정보를 담은 UserSummaryResponse
     * @throws BusinessException 이미 존재하는 사용자명인 경우 (USERNAME_ALREADY_EXISTS)
     */
    @Transactional
    public UserSummaryResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        // 관리자가 생성한 계정은 첫 로그인 시 반드시 비밀번호를 변경하도록 요구
        user.requirePasswordChange();
        return UserSummaryResponse.from(userRepository.save(user));
    }

    /**
     * 전체 사용자 목록을 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (null 불허)
     * @return 페이지 형태의 사용자 요약 정보 목록
     */
    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserSummaryResponse::from);
    }

    /**
     * 특정 사용자의 역할을 변경
     *
     * @param id   역할을 변경할 사용자의 ID (null 불허)
     * @param role 변경할 역할 문자열 (null 불허, 예: ROLE_ADMIN, ROLE_AUDITOR, ROLE_END_USER)
     * @return 역할이 변경된 사용자 요약 정보
     * @throws BusinessException 해당 ID의 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    @Transactional
    public UserSummaryResponse updateRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateRole(role);
        return UserSummaryResponse.from(user);
    }

    /**
     * 특정 사용자의 계정 활성화 또는 비활성화 상태를 변경
     *
     * @param id     상태를 변경할 사용자의 ID (null 불허)
     * @param active true이면 활성화, false이면 비활성화
     * @return 상태가 변경된 사용자 요약 정보
     * @throws BusinessException 해당 ID의 사용자가 존재하지 않는 경우 (USER_NOT_FOUND)
     */
    @Transactional
    public UserSummaryResponse updateStatus(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (active) {
            user.activate();
        } else {
            user.deactivate();
        }
        return UserSummaryResponse.from(user);
    }
}
