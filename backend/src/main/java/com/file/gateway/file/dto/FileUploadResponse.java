package com.file.gateway.file.dto;

import com.file.gateway.file.entity.FileMetadata;

/**
 * FileUploadResponse
 * - 파일 업로드 API 응답 DTO이다.
 * - 업로드 직후 클라이언트에게 파일 ID, 원본명, 크기, 처리 상태를 반환한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record FileUploadResponse(
        /** 저장된 파일의 고유 식별자 */
        Long fileId,
        /** 클라이언트가 업로드한 원본 파일명 */
        String originalName,
        /** 파일 크기 (바이트 단위) */
        Long fileSize,
        /** 현재 CDR 처리 상태 (예: PROCESSING) */
        String status
) {
    /**
     * FileMetadata 엔티티로부터 FileUploadResponse 인스턴스를 생성한다.
     *
     * @param file 변환할 FileMetadata 엔티티
     * @return 생성된 FileUploadResponse 인스턴스
     */
    public static FileUploadResponse from(FileMetadata file) {
        return new FileUploadResponse(
                file.getId(),
                file.getOriginalName(),
                file.getFileSize(),
                file.getStatus().name()
        );
    }
}
