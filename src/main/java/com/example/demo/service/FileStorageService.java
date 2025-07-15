package com.example.demo.service;

import com.example.demo.model.FileMetadata;
import com.example.demo.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final FileMetadataRepository fileMetadataRepository;

    public FileStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public FileMetadata storeFile(MultipartFile file, String subdirectory) {
        // Generate unique filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;

        try {
            // Create subdirectory if it doesn't exist
            Path targetLocation = this.fileStorageLocation.resolve(subdirectory);
            Files.createDirectories(targetLocation);

            // Store the file
            Path filePath = targetLocation.resolve(storedFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Create and save metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoredFilename(storedFilename);
            metadata.setFilePath(subdirectory + "/" + storedFilename);
            metadata.setContentType(file.getContentType());
            metadata.setFileSize(file.getSize());

            return fileMetadataRepository.save(metadata);
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFilename, ex);
        }
    }

    public Resource loadFileAsResource(String filePath) {
        try {
            Path file = this.fileStorageLocation.resolve(filePath).normalize();
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filePath);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found: " + filePath, ex);
        }
    }

    public boolean deleteFile(FileMetadata metadata) {
        try {
            Path file = this.fileStorageLocation.resolve(metadata.getFilePath()).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new RuntimeException("Error deleting file: " + metadata.getFilePath(), ex);
        }
    }

    // Generate subdirectory path based on course and lesson IDs
    public String generatePath(Long courseId, Long lessonId, String type) {
        return String.format("courses/%d/lessons/%d/%s", courseId, lessonId, type);
    }
    public FileMetadata createFileMetadata(MultipartFile file, String subdirectory) {
        // Generate unique filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;

        try {
            // Create subdirectory if it doesn't exist
            Path targetLocation = this.fileStorageLocation.resolve(subdirectory);
            Files.createDirectories(targetLocation);

            // Store the file
            Path filePath = targetLocation.resolve(storedFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Create metadata WITHOUT saving it
            FileMetadata metadata = new FileMetadata();
            metadata.setOriginalFilename(originalFilename);
            metadata.setStoredFilename(storedFilename);
            metadata.setFilePath(subdirectory + "/" + storedFilename);
            metadata.setContentType(file.getContentType());
            metadata.setFileSize(file.getSize());

            return metadata; // بدون save کردن برمی‌گردانیم
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFilename, ex);
        }
    }
    /**
     * Get file metadata by ID
     */
    public FileMetadata getFileById(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found with ID: " + fileId));
    }

    /**
     * Delete file by ID
     */
    @Transactional
    public void deleteFileById(Long fileId) {
        FileMetadata metadata = getFileById(fileId);

        // Delete physical file
        deleteFile(metadata);

        // Delete metadata record
        fileMetadataRepository.delete(metadata);
    }
}