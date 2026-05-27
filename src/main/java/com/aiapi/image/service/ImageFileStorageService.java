package com.aiapi.image.service;

import com.aiapi.common.exception.BizException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageFileStorageService {

    private final String storageDir;
    private final String responseStorageDir;

    public ImageFileStorageService(@Value("${ai.image.storage-dir:./state/ai-images}") String storageDir,
                                   @Value("${ai.image.response-storage-dir:./state/ai-image-responses}") String responseStorageDir) {
        this.storageDir = storageDir;
        this.responseStorageDir = responseStorageDir;
    }

    public StoredFile saveSourceImage(Long recordId, MultipartFile file) {
        return saveUploadImage(recordId, file, "source", "source-image");
    }

    public StoredFile saveMaskImage(Long recordId, MultipartFile file) {
        return saveUploadImage(recordId, file, "mask", "mask-image");
    }

    private StoredFile saveUploadImage(Long recordId, MultipartFile file, String prefix, String fallbackFileName) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, prefix + " image is required");
        }
        try {
            String fileName = safeFileName(file.getOriginalFilename(), fallbackFileName);
            Path target = recordDir(recordId).resolve(prefix + "-" + fileName);
            Files.write(target, file.getBytes());
            return new StoredFile(fileName, target.toString(), safeContentType(file.getContentType()), file.getSize());
        } catch (IOException ex) {
            throw new BizException(400, "failed to save " + prefix + " image");
        }
    }

    public StoredFile saveResultImage(Long recordId, byte[] content, String outputFormat, String contentType) {
        if (content == null || content.length == 0) {
            throw new BizException(502, "image api did not return image content");
        }
        try {
            String extension = normalizeExtension(outputFormat);
            String fileName = "result-" + recordId + "." + extension;
            Path target = recordDir(recordId).resolve(fileName);
            Files.write(target, content);
            return new StoredFile(fileName, target.toString(), safeContentType(contentTypeFor(extension, contentType)), (long) content.length);
        } catch (IOException ex) {
            throw new BizException(500, "failed to save generated image");
        }
    }

    public StoredFile saveResponsePayload(Long recordId, String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            throw new BizException(502, "image api response is empty");
        }
        try {
            String fileName = "response-" + recordId + ".json";
            Path target = responseRecordDir(recordId).resolve(fileName);
            byte[] content = responsePayload.getBytes(StandardCharsets.UTF_8);
            Files.write(target, content);
            return new StoredFile(fileName, target.toString(), "application/json", (long) content.length);
        } catch (IOException ex) {
            throw new BizException(500, "failed to save image api response");
        }
    }

    public FileSystemResource toResource(String storedPath) {
        Path path = resolveStoredPath(storedPath);
        if (!Files.isRegularFile(path)) {
            throw new BizException(404, "image file not found");
        }
        return new FileSystemResource(path);
    }

    public long size(String storedPath) {
        try {
            return Files.size(resolveStoredPath(storedPath));
        } catch (IOException ex) {
            throw new BizException(404, "image file not found");
        }
    }

    private Path recordDir(Long recordId) throws IOException {
        Path dir = rootDir().resolve("records").resolve(String.valueOf(recordId));
        Files.createDirectories(dir);
        return dir;
    }

    private Path rootDir() throws IOException {
        Path root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private Path responseRecordDir(Long recordId) throws IOException {
        Path dir = responseRootDir().resolve("records").resolve(String.valueOf(recordId));
        Files.createDirectories(dir);
        return dir;
    }

    private Path responseRootDir() throws IOException {
        Path root = Path.of(responseStorageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private Path resolveStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BizException(404, "image file not found");
        }
        try {
            Path root = rootDir();
            Path path = Path.of(storedPath).toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                throw new BizException(400, "invalid image file path");
            }
            return path;
        } catch (IOException ex) {
            throw new BizException(404, "image file not found");
        }
    }

    private String safeFileName(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        normalized = normalized.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeExtension(String outputFormat) {
        String normalized = outputFormat == null ? "png" : outputFormat.trim().toLowerCase(Locale.ROOT);
        if ("jpg".equals(normalized)) {
            return "jpeg";
        }
        if (!"png".equals(normalized) && !"jpeg".equals(normalized)) {
            return "png";
        }
        return normalized;
    }

    private String contentTypeFor(String extension, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return "jpeg".equals(extension) ? "image/jpeg" : "image/png";
    }

    private String safeContentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value.trim();
    }

    public record StoredFile(String fileName, String path, String contentType, Long size) {
    }
}
