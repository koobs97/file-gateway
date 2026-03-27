package com.file.gateway.file.dto;

/**
 * FileStatisticsResponse
 * - 파일 처리 통계 조회 API 응답 DTO이다.
 * - 전체 파일 수, 처리 상태별(완료/실패/처리 중) 건수, 삭제 건수, 성공률을 제공한다.
 * - 성공률은 총 파일 수 대비 완료(DONE) 건수의 백분율로 계산된다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public record FileStatisticsResponse(
        /** 논리 삭제되지 않은 전체 파일 수 */
        long total,
        /** CDR 처리 완료(DONE) 파일 수 */
        long done,
        /** CDR 처리 실패(FAIL) 파일 수 */
        long fail,
        /** CDR 처리 중(PROCESSING) 파일 수 */
        long processing,
        /** 논리 삭제된 파일 수 */
        long deleted,
        /** 성공률 (done / total * 100, total이 0이면 0.0) */
        double successRate
) {
    /**
     * 각 건수를 받아 성공률을 자동 계산하여 FileStatisticsResponse 인스턴스를 생성한다.
     *
     * @param total      전체 파일 수
     * @param done       완료 파일 수
     * @param fail       실패 파일 수
     * @param processing 처리 중 파일 수
     * @param deleted    삭제 파일 수
     * @return 성공률이 포함된 FileStatisticsResponse 인스턴스
     */
    public static FileStatisticsResponse of(long total, long done, long fail, long processing, long deleted) {
        double rate = total == 0 ? 0.0 : (done * 100.0 / total);
        return new FileStatisticsResponse(total, done, fail, processing, deleted, rate);
    }
}
