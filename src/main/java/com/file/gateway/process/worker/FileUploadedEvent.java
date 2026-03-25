package com.file.gateway.process.worker;

public record FileUploadedEvent(Long fileId, long fileSize) {}
