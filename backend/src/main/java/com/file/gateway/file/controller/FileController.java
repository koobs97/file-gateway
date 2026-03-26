package com.file.gateway.file.controller;

import com.file.gateway.common.response.ApiResponse;
import com.file.gateway.file.dto.FileDetailResponse;
import com.file.gateway.file.dto.FileEventResponse;
import com.file.gateway.file.dto.FileUploadResponse;
import com.file.gateway.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "File", description = "파일 업로드 및 무해화 API")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "파일 업로드", description = "MS Office 파일을 업로드합니다. (docx/xlsx/pptx)")
    @PreAuthorize("hasAnyRole('ADMIN', 'END_USER', 'API_CLIENT')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @RequestPart("file") MultipartFile file) throws IOException {
        FileUploadResponse response = fileService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @Operation(summary = "파일 목록 조회", description = "업로드된 파일 목록을 페이징으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FileDetailResponse>>> getFiles(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.getFiles(pageable)));
    }

    @Operation(summary = "파일 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.getFile(id)));
    }

    @Operation(summary = "파일 삭제 (soft delete)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable("id") Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "무해화 파일 다운로드", description = "처리 완료(DONE) 상태인 파일만 다운로드 가능합니다.")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) throws IOException {
        FileDetailResponse fileInfo = fileService.getFile(id);
        Resource resource = fileService.downloadSanitizedFile(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("sanitized_" + fileInfo.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    @Operation(summary = "파일 이벤트 이력 조회")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<FileEventResponse>>> getEvents(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.getEvents(id)));
    }
}
