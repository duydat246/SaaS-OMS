package com.saasoms.common.media;

import com.saasoms.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lưu tệp ảnh trên ổ đĩa cục bộ.
 * Khoá lưu trữ có dạng {@code <tenantId>/<uuid>.<ext>}.
 */
@Slf4j
@Service
public class LocalMediaStorage {

    private static final String DEFAULT_STORAGE_DIR = "uploads";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Pattern SAFE_KEY =
            Pattern.compile("^\\d{1,19}/[A-Za-z0-9_-]{1,64}\\.(jpg|jpeg|png|webp|gif)$");
    private static final String FILE_NOT_FOUND_MESSAGE = "Không tìm thấy tệp ảnh";

    private final Path root;

    public LocalMediaStorage(MediaProperties mediaProperties) {
        String configured = mediaProperties.getStorageDir();
        String dir = (configured == null || configured.isBlank()) ? DEFAULT_STORAGE_DIR : configured;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Không tạo được thư mục lưu ảnh: " + root, ex);
        }
    }

    /**
     * Ghi nội dung tệp xuống đĩa và trả khoá lưu trữ.
     */
    public String store(Long tenantId, byte[] content, String extension) {
        if (tenantId == null) {
            throw BusinessException.forbidden("Không xác định được cửa hàng");
        }
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("Tệp ảnh rỗng");
        }

        String ext = normalizeExtension(extension);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path tenantDir = root.resolve(String.valueOf(tenantId)).normalize();
        Path target = tenantDir.resolve(fileName).normalize();

        if (!target.startsWith(root) || !tenantDir.startsWith(root)) {
            throw BusinessException.badRequest("Đường dẫn tệp ảnh không hợp lệ");
        }

        try {
            Files.createDirectories(tenantDir);
            Files.write(target, content);
        } catch (IOException | InvalidPathException ex) {
            log.error("Không ghi được tệp ảnh vào {}", target, ex);
            throw new BusinessException("Không lưu được tệp ảnh, vui lòng thử lại",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return tenantId + "/" + fileName;
    }

    /**
     * Đọc nội dung tệp theo khoá lưu trữ.
     */
    public byte[] read(String key) {
        Path target = resolve(key);
        if (target == null || !Files.isRegularFile(target)) {
            throw BusinessException.notFound(FILE_NOT_FOUND_MESSAGE);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            log.warn("Không đọc được tệp ảnh {}", target, ex);
            throw BusinessException.notFound(FILE_NOT_FOUND_MESSAGE);
        }
    }

    /**
     * Xoá tệp (best-effort): không ném lỗi nếu tệp không còn.
     */
    public void delete(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            return;
        }
        Path target = resolve(key);
        if (target == null) {
            return;
        }
        try {
            boolean removed = Files.deleteIfExists(target);
            if (!removed) {
                log.debug("Tệp ảnh {} đã không còn trên đĩa", key);
            }
        } catch (IOException ex) {
            log.warn("Không xoá được tệp ảnh {}", key, ex);
        }
    }

    private Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            return null;
        }
        try {
            Path target = root.resolve(key).normalize();
            return target.startsWith(root) ? target : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private static String normalizeExtension(String extension) {
        String raw = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        String ext = raw.startsWith(".") ? raw.substring(1) : raw;
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.badRequest("Định dạng tệp ảnh không được hỗ trợ");
        }
        return ext;
    }
}
