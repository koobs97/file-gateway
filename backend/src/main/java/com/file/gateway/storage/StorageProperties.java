package com.file.gateway.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String basePath = "./uploads";
    private long maxFileSize = 104857600L;
    private String allowedExtensions = "docx,xlsx,pptx";

    public List<String> getAllowedExtensionList() {
        return Arrays.asList(allowedExtensions.split(","));
    }
}
