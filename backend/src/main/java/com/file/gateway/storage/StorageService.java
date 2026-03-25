package com.file.gateway.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    /**
     * 파일을 스트리밍 방식으로 저장한다.
     *
     * @param in         저장할 InputStream
     * @param storedName 저장 파일명 (UUID.ext)
     * @param subDir     서브 디렉토리 (original / sanitized)
     * @return 저장된 절대 경로
     */
    String save(InputStream in, String storedName, String subDir) throws IOException;

    InputStream load(String path) throws IOException;

    void delete(String path);

    boolean exists(String path);

    /** 저장 경로를 반환한다 (파일 존재 여부와 무관). */
    String resolvePath(String storedName, String subDir);
}
