package com.file.gateway.file.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetadataTest {

    private FileMetadata createFile() {
        return FileMetadata.builder()
                .originalName("test.docx")
                .storedName("uuid-1234.docx")
                .fileSize(1024L)
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .storagePath("/uploads/original/uuid-1234.docx")
                .storageType(StorageType.LOCAL)
                .status(FileStatus.UPLOADED)
                .build();
    }

    @Test
    @DisplayName("초기 생성 시 isDeleted()는 false를 반환한다")
    void isDeleted_ReturnsFalse_WhenInitiallyCreated() {
        // given
        FileMetadata file = createFile();

        // when & then
        assertThat(file.isDeleted()).isFalse();
        assertThat(file.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("delete() 호출 시 deletedAt이 설정되고 isDeleted()가 true를 반환한다")
    void delete_SetsDeletedAt_AndIsDeletedReturnsTrue() {
        // given
        FileMetadata file = createFile();

        // when
        file.delete();

        // then
        assertThat(file.isDeleted()).isTrue();
        assertThat(file.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateStatus() 호출 시 상태가 변경된다")
    void updateStatus_ChangesStatusSuccessfully() {
        // given
        FileMetadata file = createFile();
        assertThat(file.getStatus()).isEqualTo(FileStatus.UPLOADED);

        // when
        file.updateStatus(FileStatus.DONE);

        // then
        assertThat(file.getStatus()).isEqualTo(FileStatus.DONE);
    }
}
