package org.mingharness.context;

import org.mingharness.common.BusinessException;
import org.mingharness.config.DocumentImportProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** 将 multipart 文件短暂落盘，避免异步解析依赖请求线程和内存中的 byte[]。 */
@Service
public class DocumentImportStorage {

    private final Path root;

    public DocumentImportStorage(DocumentImportProperties properties) {
        try {
            root = Path.of(properties.importDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建知识文档异步导入目录", exception);
        }
    }

    public Path stage(MultipartFile file) {
        try {
            Path target = Files.createTempFile(root, "document-import-", ".upload");
            file.transferTo(target);
            return target;
        } catch (IOException | IllegalStateException exception) {
            throw new BusinessException(HttpStatus.INSUFFICIENT_STORAGE,
                    "DOCUMENT_IMPORT_STORAGE_FAILED", "暂时无法保存上传文件，请稍后重试");
        }
    }

    public void delete(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return;
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 暂存文件清理失败不应覆盖已经完成的文档状态；后续启动清理会再次处理。
        }
    }

    /** 启动时清理由已完成/失败任务遗留、且不再被 PROCESSING 文档引用的暂存文件。 */
    public void cleanupUnreferenced(Iterable<String> referencedPaths) {
        Set<Path> referenced = new HashSet<>();
        if (referencedPaths != null) {
            for (String path : referencedPaths) {
                if (path != null && !path.isBlank()) {
                    try {
                        referenced.add(Path.of(path).toAbsolutePath().normalize());
                    } catch (RuntimeException ignored) {
                        // 无效的历史路径不会阻止专用暂存目录的清理。
                    }
                }
            }
        }
        try (var files = Files.list(root)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> !referenced.contains(path))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 下次启动继续清理，不覆盖正常导入状态。
                        }
                    });
        } catch (IOException ignored) {
            // 暂存目录不可读时不阻断应用启动；导入任务仍会按数据库状态恢复。
        }
    }
}
