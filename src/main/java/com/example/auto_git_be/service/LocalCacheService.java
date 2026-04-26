package com.example.auto_git_be.service;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalCacheService {

    private final MinioService minioService;

    private final String CACHE_DIR = System.getProperty("user.dir") + "/local_cache/tasks/";

    // Map chứa các ổ khóa để chặn luồng đồng thời (Chống Thundering Herd)
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public LocalCacheService(MinioService minioService) {
        this.minioService = minioService;
    }

    public String getTestcaseContent(Long taskId, String objectKey, String fileName) {
        File taskDir = new File(CACHE_DIR + taskId);
        if (!taskDir.exists()) {
            taskDir.mkdirs();
        }

        File localFile = new File(taskDir, fileName);

        if (localFile.exists()) {
            return readFileContent(localFile);
        }

        Object lock = locks.computeIfAbsent(objectKey, k -> new Object());

        synchronized (lock) {
            if (localFile.exists()) {
                return readFileContent(localFile);
            }

            try (InputStream is = minioService.downloadFile(objectKey)) {
                Files.copy(is, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return readFileContent(localFile);
            } catch (Exception e) {
                throw new RuntimeException("Lỗi tải file từ MinIO về Cache: " + objectKey, e);
            }
        }
    }

    private String readFileContent(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi đọc file từ Local Cache: " + file.getName(), e);
        }
    }
}