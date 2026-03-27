package com.file.gateway.file.entity;

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
 * FileMetadata
 * - 업로드된 파일의 메타데이터를 저장하는 JPA 엔티티이다.
 * - 원본 파일명, 저장명(UUID 기반), 크기, MIME 타입, 저장 경로, 처리 상태 등을 관리한다.
 * - 논리 삭제(soft delete) 방식을 사용하며 deletedAt 값이 존재하면 삭제된 파일로 간주한다.
 * - Spring Data JPA Auditing을 통해 생성/수정 시각이 자동으로 기록된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Entity
@Table(name = "file")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileMetadata {

    /** 파일 고유 식별자 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 클라이언트가 업로드한 원본 파일명 */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** 저장소에 실제로 저장된 파일명 (UUID.확장자 형식) */
    @Column(name = "stored_name", nullable = false)
    private String storedName;

    /** 파일 크기 (바이트 단위) */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** 파일의 MIME 타입 (예: application/vnd.openxmlformats-officedocument.wordprocessingml.document) */
    @Column(name = "mime_type")
    private String mimeType;

    /** 파일이 저장된 절대 경로 */
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /** 파일이 저장된 스토리지 종류 (LOCAL, NAS, S3, MINIO) */
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType;

    /** CDR 처리 상태 (UPLOADED, PROCESSING, DONE, FAIL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FileStatus status;

    /** 파일을 업로드한 사용자명 */
    @Column(name = "uploader_name")
    private String uploaderName;

    /** 논리 삭제 시각 (null이면 활성 파일) */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 레코드 생성 시각 (자동 설정, 변경 불가) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 레코드 최종 수정 시각 (자동 갱신) */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * FileMetadata 빌더 생성자.
     * storageType과 status가 null이면 각각 LOCAL, UPLOADED로 기본값을 설정한다.
     *
     * @param originalName 원본 파일명
     * @param storedName   저장 파일명 (UUID.확장자)
     * @param fileSize     파일 크기 (바이트)
     * @param mimeType     MIME 타입
     * @param storagePath  저장 경로
     * @param storageType  스토리지 종류 (null이면 LOCAL)
     * @param status       처리 상태 (null이면 UPLOADED)
     * @param uploaderName 업로드한 사용자명
     */
    @Builder
    public FileMetadata(String originalName, String storedName, Long fileSize,
                        String mimeType, String storagePath, StorageType storageType,
                        FileStatus status, String uploaderName) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.storagePath = storagePath;
        this.storageType = storageType != null ? storageType : StorageType.LOCAL;
        this.status = status != null ? status : FileStatus.UPLOADED;
        this.uploaderName = uploaderName;
    }

    /**
     * CDR 처리 상태를 갱신한다.
     *
     * @param status 변경할 FileStatus 값
     */
    public void updateStatus(FileStatus status) {
        this.status = status;
    }

    /**
     * 파일을 논리 삭제 처리한다. deletedAt에 현재 시각을 기록한다.
     */
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 파일의 논리 삭제 여부를 반환한다.
     *
     * @return deletedAt이 설정되어 있으면 true
     */
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
