package com.example.demo.controller;

import com.example.demo.dto.FileMetadataDTO;
import com.example.demo.model.FileMetadata;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.DTOMapperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;

    public FileController(FileStorageService fileStorageService, DTOMapperService dtoMapperService) {
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a file",
            description = "Upload a file and return its metadata with ID for use in other endpoints"
    )
    public ResponseEntity<FileMetadataDTO> uploadFile(
            @Parameter(description = "File to upload") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Category/type of file") @RequestParam(value = "category", defaultValue = "general") String category) {
        
        if (file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        // Store file in general category subdirectory
        String subdirectory = "uploads/" + category;
        FileMetadata metadata = fileStorageService.storeFile(file, subdirectory);
        
        FileMetadataDTO dto = dtoMapperService.mapToFileMetadataDTO(metadata);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{fileId}")
    @Operation(
            summary = "Download file",
            description = "Download file by its ID"
    )
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "File ID") @PathVariable Long fileId,
            HttpServletRequest request) {

        FileMetadata metadata = fileStorageService.getFileById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());

        // Try to determine content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // Fallback to stored content type
        }

        if (contentType == null) {
            contentType = metadata.getContentType();
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + metadata.getOriginalFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/{fileId}/info")
    @Operation(
            summary = "Get file metadata",
            description = "Get file metadata information by ID"
    )
    public ResponseEntity<FileMetadataDTO> getFileInfo(@PathVariable Long fileId) {
        FileMetadata metadata = fileStorageService.getFileById(fileId);
        FileMetadataDTO dto = dtoMapperService.mapToFileMetadataDTO(metadata);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{fileId}")
    @Operation(
            summary = "Delete file",
            description = "Delete file by its ID"
    )
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId) {
        fileStorageService.deleteFileById(fileId);
        return ResponseEntity.noContent().build();
    }
}