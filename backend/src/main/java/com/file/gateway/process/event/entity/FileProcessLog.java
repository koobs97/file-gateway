package com.file.gateway.process.event.entity;

import com.file.gateway.file.entity.FileMetadata;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * FileProcessLog
 * - CDR 처리 각 단계(업로드, 분석, 무해화, 저장)의 실행 결과를 기록하는 JPA 엔티티 (file_process_log 테이블)
 * - 단계별 성공/실패 여부와 상세 메시지를 저장하여 운영 추적 및 감사에 활용
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Entity
@Table(name = "file_process_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileProcessLog {

    /** 로그 고유 식별자 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 로그가 속한 파일 메타데이터 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    /** 처리 단계 (UPLOAD, ANALYSIS, SANITIZE, SAVE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private ProcessStep step;

    /** 처리 결과 상태 (SUCCESS, FAIL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessStatus status;

    /** 처리 결과에 대한 상세 설명 메시지 */
    @Column(name = "message")
    private String message;

    /** 로그 생성 일시 (자동 기록, 수정 불가) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 파일 처리 로그 엔티티를 생성한다.
     *
     * @param file    연관 파일 메타데이터
     * @param step    처리 단계
     * @param status  처리 결과 상태
     * @param message 상세 메시지
     */
    @Builder
    public FileProcessLog(FileMetadata file, ProcessStep step, ProcessStatus status, String message) {
        this.file = file;
        this.step = step;
        this.status = status;
        this.message = message;
    }
}
