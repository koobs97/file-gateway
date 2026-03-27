package com.file.gateway.admin;

import com.file.gateway.admin.dto.ApiClientResponse;
import com.file.gateway.admin.dto.CreateApiClientRequest;
import com.file.gateway.client.entity.ApiClient;
import com.file.gateway.client.repository.ApiClientRepository;
import com.file.gateway.common.exception.BusinessException;
import com.file.gateway.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AdminApiClientService
 * - 관리자 권한으로 수행하는 API 클라이언트 관리 비즈니스 로직 처리
 * - API 클라이언트 생성 시 UUID 기반 API 키를 자동 발급하고 ACTIVE 상태로 초기화
 * - 클라이언트 비활성화(논리 삭제) 기능 제공
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class AdminApiClientService {

    /** API 클라이언트 엔티티 조회 및 저장을 위한 Repository */
    private final ApiClientRepository apiClientRepository;

    /**
     * 전체 API 클라이언트 목록을 페이지네이션하여 조회
     *
     * @param pageable 페이지 번호, 크기, 정렬 조건 (null 불허)
     * @return 페이지 형태의 API 클라이언트 응답 DTO 목록
     */
    @Transactional(readOnly = true)
    public Page<ApiClientResponse> getClients(Pageable pageable) {
        return apiClientRepository.findAll(pageable).map(ApiClientResponse::from);
    }

    /**
     * 새 API 클라이언트를 생성하고 UUID로 API 키를 자동 발급
     *
     * @param request 클라이언트 이름을 담은 생성 요청 DTO (null 불허)
     * @return 저장된 API 클라이언트 정보를 담은 응답 DTO (API 키 포함)
     */
    @Transactional
    public ApiClientResponse createClient(CreateApiClientRequest request) {
        // UUID를 사용하여 충돌 가능성이 없는 고유한 API 키를 자동 생성
        String apiKey = UUID.randomUUID().toString();
        ApiClient client = ApiClient.builder()
                .clientName(request.clientName())
                .apiKey(apiKey)
                .status("ACTIVE")
                .build();
        return ApiClientResponse.from(apiClientRepository.save(client));
    }

    /**
     * 특정 API 클라이언트를 비활성화 처리 (논리 삭제)
     *
     * @param id 비활성화할 API 클라이언트의 ID (null 불허)
     * @throws BusinessException 해당 ID의 API 클라이언트가 존재하지 않는 경우 (API_CLIENT_NOT_FOUND)
     */
    @Transactional
    public void deactivate(Long id) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_CLIENT_NOT_FOUND));
        client.deactivate();
    }
}
