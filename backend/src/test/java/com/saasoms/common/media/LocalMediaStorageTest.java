package com.saasoms.common.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMediaStorageTest {

    @TempDir
    Path tempDir;

    private LocalMediaStorage storage;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties();
        properties.setStorageDir(tempDir.toString());
        storage = new LocalMediaStorage(properties);
    }

    @Test
    void openResource_existingFile_streamsContent() throws IOException {
        String key = storage.store(1L, new byte[]{1, 2, 3}, "jpg");

        Resource resource = storage.openResource(key).orElseThrow();

        assertThat(resource.contentLength()).isEqualTo(3);
        try (InputStream in = resource.getInputStream()) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void openResource_missingFile_returnsEmpty() {
        assertThat(storage.openResource("1/khongco.jpg")).isEmpty();
    }

    @Test
    void openResource_unsafeKey_returnsEmpty() {
        assertThat(storage.openResource("../../etc/passwd")).isEmpty();
        assertThat(storage.openResource("1/../../a.jpg")).isEmpty();
        assertThat(storage.openResource(null)).isEmpty();
    }
}
