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
 * FileEvent
 * - 파일 처리 생명주기 이벤트를 기록하는 JPA 엔티티 (file_event 테이블)
 * - CDR 파이프라인의 상태 변화(QUEUED → START → DONE/FAIL)를 시계열로 추적
 * - payload 필드에 이벤트별 부가 정보를 텍스트 형태로 저장
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Entity
@Table(name = "file_event")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileEvent {

    /** 이벤트 고유 식별자 (자동 증가) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트가 연관된 파일 메타데이터 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    /** 이벤트 유형 (QUEUED, START, DONE, FAIL) */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    /** 이벤트 관련 부가 데이터 (선택적) */
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    /** 이벤트 생성 일시 (자동 기록, 수정 불가) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 파일 이벤트 엔티티를 생성한다.
     *
     * @param file      연관 파일 메타데이터
     * @param eventType 이벤트 유형
     * @param payload   이벤트 부가 데이터
     */
    @Builder
    public FileEvent(FileMetadata file, EventType eventType, String payload) {
        this.file = file;
        this.eventType = eventType;
        this.payload = payload;
    }
}
