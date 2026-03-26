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

@Service
@RequiredArgsConstructor
public class AdminApiClientService {

    private final ApiClientRepository apiClientRepository;

    @Transactional(readOnly = true)
    public Page<ApiClientResponse> getClients(Pageable pageable) {
        return apiClientRepository.findAll(pageable).map(ApiClientResponse::from);
    }

    @Transactional
    public ApiClientResponse createClient(CreateApiClientRequest request) {
        String apiKey = UUID.randomUUID().toString();
        ApiClient client = ApiClient.builder()
                .clientName(request.clientName())
                .apiKey(apiKey)
                .status("ACTIVE")
                .build();
        return ApiClientResponse.from(apiClientRepository.save(client));
    }

    @Transactional
    public void deactivate(Long id) {
        ApiClient client = apiClientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_CLIENT_NOT_FOUND));
        client.deactivate();
    }
}
