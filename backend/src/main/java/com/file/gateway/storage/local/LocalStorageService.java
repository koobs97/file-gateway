package com.file.gateway.storage.local;

import com.file.gateway.storage.StorageProperties;
import com.file.gateway.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * LocalStorageService
 * - StorageService 인터페이스의 로컬 파일 시스템 구현체이다.
 * - 파일을 basePath/{subDir}/{storedName} 경로에 저장하고 관리한다.
 * - 파일 저장 시 스트리밍 방식(Files.copy)을 사용하여 메모리 적재를 최소화한다.
 * - 삭제 시 Files.deleteIfExists를 사용하여 파일이 없어도 예외를 발생시키지 않는다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    /** 파일 저장 루트 경로 (절대 경로로 정규화됨) */
    private final Path basePath;

    /**
     * StorageProperties에서 basePath를 읽어 절대 경로로 초기화한다.
     *
     * @param properties 저장소 설정 프로퍼티 (basePath 포함)
     */
    public LocalStorageService(StorageProperties properties) {
        this.basePath = Paths.get(properties.getBasePath()).toAbsolutePath().normalize();
    }

    /**
     * 파일을 스트리밍 방식으로 로컬 파일 시스템에 저장한다.
     * 대상 디렉토리가 없으면 자동으로 생성한다.
     *
     * @param in         저장할 InputStream
     * @param storedName 저장 파일명 (UUID.확장자)
     * @param subDir     서브 디렉토리 (original / sanitized)
     * @return 저장된 파일의 절대 경로 문자열
     * @throws IOException 디렉토리 생성 또는 파일 복사 중 I/O 오류 발생 시
     */
    @Override
    public String save(InputStream in, String storedName, String subDir) throws IOException {
        Path dir = basePath.resolve(subDir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(storedName);
        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        log.debug("파일 저장 완료: {}", dest);
        return dest.toString();
    }

    /**
     * 지정된 경로의 파일을 InputStream으로 열어 반환한다.
     *
     * @param path 읽을 파일의 절대 경로
     * @return 파일 InputStream
     * @throws IOException 파일 열기 중 I/O 오류 발생 시
     */
    @Override
    public InputStream load(String path) throws IOException {
        return Files.newInputStream(Paths.get(path));
    }

    /**
     * 지정된 경로의 파일을 삭제한다. 파일이 존재하지 않아도 경고 로그만 남기고 예외를 발생시키지 않는다.
     *
     * @param path 삭제할 파일의 절대 경로
     */
    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", path);
        }
    }

    /**
     * 지정된 경로에 파일이 존재하는지 확인한다.
     *
     * @param path 확인할 파일의 절대 경로
     * @return 파일이 존재하면 true, 아니면 false
     */
    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    /**
     * 저장 파일명과 서브 디렉토리를 기반으로 파일 경로를 계산하여 반환한다.
     * 파일의 실제 존재 여부와 무관하게 경로 문자열만 반환한다.
     *
     * @param storedName 저장 파일명 (UUID.확장자)
     * @param subDir     서브 디렉토리 (original / sanitized)
     * @return 계산된 파일 절대 경로 문자열
     */
    @Override
    public String resolvePath(String storedName, String subDir) {
        return basePath.resolve(subDir).resolve(storedName).toString();
    }
}
