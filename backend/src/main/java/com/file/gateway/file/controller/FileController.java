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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FileController
 * - 파일 업로드, 목록 조회, 단건 조회, 삭제, 다운로드, 이벤트 이력 조회 REST API를 제공한다.
 * - 역할(ADMIN, AUDITOR, END_USER, API_CLIENT)에 따른 접근 제어를 적용한다.
 * - ADMIN·AUDITOR는 전체 파일에 접근 가능하며, 일반 사용자는 본인 업로드 파일만 접근 가능하다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Tag(name = "File", description = "파일 업로드 및 무해화 API")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    /** 파일 비즈니스 로직을 처리하는 서비스 */
    private final FileService fileService;

    /**
     * MS Office 파일을 업로드하고 CDR 처리를 트리거한다.
     *
     * @param principal 현재 인증된 사용자 정보
     * @param file      업로드할 MultipartFile (docx/xlsx/pptx)
     * @return 업로드된 파일 메타데이터 응답 (HTTP 201)
     * @throws IOException 파일 스트리밍 저장 중 I/O 오류 발생 시
     */
    @Operation(summary = "파일 업로드", description = "MS Office 파일을 업로드합니다. (docx/xlsx/pptx)")
    @PreAuthorize("hasAnyRole('ADMIN', 'END_USER', 'API_CLIENT')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(
            @AuthenticationPrincipal UserDetails principal,
            @RequestPart("file") MultipartFile file) throws IOException {
        FileUploadResponse response = fileService.upload(file, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    /**
     * 업로드된 파일 목록을 페이징 방식으로 조회한다.
     * ADMIN·AUDITOR는 전체 목록을, 일반 사용자는 본인 업로드 목록만 조회한다.
     *
     * @param principal 현재 인증된 사용자 정보
     * @param keyword   파일명 검색 키워드 (선택)
     * @param status    파일 처리 상태 필터 (선택, UPLOADED/PROCESSING/DONE/FAIL)
     * @param pageable  페이징 정보 (기본: 최신순 20건)
     * @return 파일 상세 정보 페이지 응답
     */
    @Operation(summary = "파일 목록 조회", description = "업로드된 파일 목록을 페이징으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<FileDetailResponse>>> getFiles(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean privileged = isPrivileged(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                fileService.getFiles(keyword, status, pageable, principal.getUsername(), privileged)));
    }

    /**
     * 특정 파일의 상세 정보를 단건 조회한다.
     *
     * @param principal 현재 인증된 사용자 정보
     * @param id        조회할 파일 ID
     * @return 파일 상세 정보 응답
     */
    @Operation(summary = "파일 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable("id") Long id) {
        boolean privileged = isPrivileged(principal);
        return ResponseEntity.ok(ApiResponse.ok(fileService.getFile(id, principal.getUsername(), privileged)));
    }

    /**
     * 파일을 논리 삭제(soft delete) 처리한다. ADMIN 역할만 호출 가능하다.
     *
     * @param id 삭제할 파일 ID
     * @return 빈 성공 응답
     */
    @Operation(summary = "파일 삭제 (soft delete)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable("id") Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 무해화 처리가 완료된 파일을 다운로드한다. DONE 상태인 파일만 다운로드 가능하다.
     *
     * @param principal 현재 인증된 사용자 정보
     * @param id        다운로드할 파일 ID
     * @return 무해화된 파일 리소스 (Content-Disposition: attachment)
     * @throws IOException 파일 스트림 읽기 중 I/O 오류 발생 시
     */
    @Operation(summary = "무해화 파일 다운로드", description = "처리 완료(DONE) 상태인 파일만 다운로드 가능합니다.")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable("id") Long id) throws IOException {
        boolean privileged = isPrivileged(principal);
        FileDetailResponse fileInfo = fileService.getFile(id, principal.getUsername(), privileged);
        Resource resource = fileService.downloadSanitizedFile(id, principal.getUsername(), privileged);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("sanitized_" + fileInfo.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    /**
     * 특정 파일에 기록된 이벤트 이력을 시간 순으로 조회한다. ADMIN·AUDITOR 역할만 호출 가능하다.
     *
     * @param id 이벤트를 조회할 파일 ID
     * @return 파일 이벤트 이력 목록 응답
     */
    @Operation(summary = "파일 이벤트 이력 조회")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<FileEventResponse>>> getEvents(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(fileService.getEvents(id)));
    }

    /**
     * 현재 사용자가 ADMIN 또는 AUDITOR 역할을 보유하는지 확인한다.
     *
     * @param principal 현재 인증된 사용자 정보
     * @return ADMIN 또는 AUDITOR 역할 보유 여부
     */
    private boolean isPrivileged(UserDetails principal) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_AUDITOR"));
    }
}
