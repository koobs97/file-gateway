package com.file.gateway.process.worker;

import java.time.LocalDateTime;

public record FileStatusNotification(Long fileId, String status, LocalDateTime timestamp) {}
