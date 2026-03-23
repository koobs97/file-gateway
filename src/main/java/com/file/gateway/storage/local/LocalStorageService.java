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

@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private final Path basePath;

    public LocalStorageService(StorageProperties properties) {
        this.basePath = Paths.get(properties.getBasePath()).toAbsolutePath().normalize();
    }

    @Override
    public String save(InputStream in, String storedName, String subDir) throws IOException {
        Path dir = basePath.resolve(subDir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(storedName);
        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        log.debug("파일 저장 완료: {}", dest);
        return dest.toString();
    }

    @Override
    public InputStream load(String path) throws IOException {
        return Files.newInputStream(Paths.get(path));
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {}", path);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public String resolvePath(String storedName, String subDir) {
        return basePath.resolve(subDir).resolve(storedName).toString();
    }
}
