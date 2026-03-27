package com.file.gateway.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * StorageService
 * - 파일 저장소에 대한 추상 인터페이스이다.
 * - 로컬 파일 시스템, NAS, S3, MinIO 등 다양한 스토리지 구현체로 교체 가능하도록 설계되었다.
 * - 파일 저장·로드·삭제·존재 확인·경로 계산 기능을 정의한다.
 *
 * @author 구본상
 * @since 2026-03-26
 */
public interface StorageService {

    /**
     * 파일을 스트리밍 방식으로 저장한다.
     *
     * @param in         저장할 InputStream
     * @param storedName 저장 파일명 (UUID.ext)
     * @param subDir     서브 디렉토리 (original / sanitized)
     * @return 저장된 절대 경로
     * @throws IOException 파일 저장 중 I/O 오류 발생 시
     */
    String save(InputStream in, String storedName, String subDir) throws IOException;

    /**
     * 지정된 경로의 파일을 InputStream으로 읽어 반환한다.
     *
     * @param path 읽을 파일의 절대 경로
     * @return 파일 InputStream
     * @throws IOException 파일 읽기 중 I/O 오류 발생 시
     */
    InputStream load(String path) throws IOException;

    /**
     * 지정된 경로의 파일을 삭제한다. 파일이 존재하지 않아도 예외를 발생시키지 않는다.
     *
     * @param path 삭제할 파일의 절대 경로
     */
    void delete(String path);

    /**
     * 지정된 경로에 파일이 존재하는지 확인한다.
     *
     * @param path 확인할 파일의 절대 경로
     * @return 파일이 존재하면 true, 아니면 false
     */
    boolean exists(String path);

    /**
     * 저장 경로를 반환한다 (파일 존재 여부와 무관).
     *
     * @param storedName 저장 파일명 (UUID.확장자)
     * @param subDir     서브 디렉토리 (original / sanitized)
     * @return 계산된 파일 경로 문자열
     */
    String resolvePath(String storedName, String subDir);
}
