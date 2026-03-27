package com.file.gateway.file.entity;

/**
 * StorageType
 * - 파일이 저장된 스토리지 종류를 나타내는 열거형이다.
 * - StorageService 구현체 선택 및 파일 메타데이터 기록에 사용된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public enum StorageType {
    /** 로컬 파일 시스템 저장소 */
    LOCAL,
    /** 네트워크 결합 스토리지 (NAS) */
    NAS,
    /** Amazon S3 오브젝트 스토리지 */
    S3,
    /** MinIO 오브젝트 스토리지 */
    MINIO
}
