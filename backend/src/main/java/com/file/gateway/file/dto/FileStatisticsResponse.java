package com.file.gateway.file.dto;

public record FileStatisticsResponse(
        long total,
        long done,
        long fail,
        long processing,
        long deleted,
        double successRate
) {
    public static FileStatisticsResponse of(long total, long done, long fail, long processing, long deleted) {
        double rate = total == 0 ? 0.0 : (done * 100.0 / total);
        return new FileStatisticsResponse(total, done, fail, processing, deleted, rate);
    }
}
