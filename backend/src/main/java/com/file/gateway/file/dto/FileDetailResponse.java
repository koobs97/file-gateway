package com.file.gateway.file.dto;

import com.file.gateway.file.entity.FileMetadata;

import java.time.LocalDateTime;

/**
 * FileDetailResponse
 * - 파일 상세 조회 API 응답 DTO이다.
 * - 파일 목록 및 단건 조회 시 공통으로 사용된다.
 * - 파일 ID, 원본명, 크기, MIME 타입, 스토리지 종류, 처리 상태, 생성·수정 시각을 포함한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record FileDetailResponse(
        /** 파일 고유 식별자 */
        Long id,
        /** 클라이언트가 업로드한 원본 파일명 */
        String originalName,
        /** 파일 크기 (바이트 단위) */
        Long fileSize,
        /** 파일의 MIME 타입 */
        String mimeType,
        /** 파일이 저장된 스토리지 종류 (예: LOCAL, S3) */
        String storageType,
        /** CDR 처리 상태 (예: PROCESSING, DONE, FAIL) */
        String status,
        /** 레코드 생성 시각 */
        LocalDateTime createdAt,
        /** 레코드 최종 수정 시각 */
        LocalDateTime updatedAt
) {
    /**
     * FileMetadata 엔티티로부터 FileDetailResponse 인스턴스를 생성한다.
     *
     * @param file 변환할 FileMetadata 엔티티
     * @return 생성된 FileDetailResponse 인스턴스
     */
    public static FileDetailResponse from(FileMetadata file) {
        return new FileDetailResponse(
                file.getId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getMimeType(),
                file.getStorageType().name(),
                file.getStatus().name(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
