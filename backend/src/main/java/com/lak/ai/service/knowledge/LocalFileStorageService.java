package com.lak.ai.service.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘文件存储服务 — 开发/生产默认实现。
 * <p>
 * 存储路径: ./data/files/{yyyyMM}/{docId}.{ext}
 */
@Slf4j
@Service
public class LocalFileStorageService {

    private final Path rootDir;

    public LocalFileStorageService(@Value("${lak.storage.local-path:./data/files}") String localPath) {
        this.rootDir = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录: " + rootDir, e);
        }
        log.info("文件存储目录: {}", rootDir);
    }

    /**
     * 保存上传文件，返回相对路径。
     */
    public String save(MultipartFile file, String docId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String filename = docId + ext;

        Path targetDir = rootDir.resolve(monthDir);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(filename).normalize();
        if (!targetFile.startsWith(rootDir)) {
            throw new IOException("非法文件路径");
        }
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = monthDir + "/" + filename;
        log.info("文件保存成功, path={}, size={}bytes", relativePath, file.getSize());
        return relativePath;
    }

    /**
     * 读取文件。
     */
    public InputStream read(String fileUrl) throws IOException {
        Path filePath = rootDir.resolve(fileUrl).normalize();
        if (!filePath.startsWith(rootDir)) {
            throw new IOException("非法文件路径: " + fileUrl);
        }
        return Files.newInputStream(filePath);
    }

    /**
     * 删除文件。
     */
    public boolean delete(String fileUrl) {
        try {
            Path filePath = rootDir.resolve(fileUrl).normalize();
            if (!filePath.startsWith(rootDir)) {
                log.warn("拒绝删除非存储目录下的文件: {}", fileUrl);
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("文件已删除: {}", fileUrl);
            }
            return deleted;
        } catch (IOException e) {
            log.warn("文件删除失败: {}", fileUrl, e);
            return false;
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return "";
        String ext = filename.substring(dot).toLowerCase();
        if (ext.contains("/") || ext.contains("\\")) return "";
        return ext;
    }
}
