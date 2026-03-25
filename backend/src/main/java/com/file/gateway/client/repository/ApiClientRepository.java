package com.file.gateway.client.repository;

import com.file.gateway.client.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {

    Optional<ApiClient> findByApiKey(String apiKey);
}
