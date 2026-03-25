package com.file.gateway.process.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SanitizeEventListenerTest {

    @Mock
    private SanitizeWorker sanitizeWorker;

    @InjectMocks
    private SanitizeEventListener listener;

    @Test
    @DisplayName("FileUploadedEvent 수신 시 sanitizeWorker.processAsync()를 호출한다")
    void onFileUploaded_CallsProcessAsync() {
        // given
        FileUploadedEvent event = new FileUploadedEvent(1L, 1024L);

        // when
        listener.onFileUploaded(event);

        // then
        verify(sanitizeWorker).processAsync(1L);
    }
}
