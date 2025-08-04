package com.example.demo.controller;

import com.example.demo.model.FileMetadata;
import com.example.demo.model.User;
import com.example.demo.service.ContentService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.UserService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/video")
public class VideoStreamingController {

    private final ContentService contentService;
    private final FileStorageService fileStorageService;
    private final UserService userService;
    private final ConcurrentHashMap<String, VideoTokenInfo> videoTokens = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public VideoStreamingController(ContentService contentService, 
                                  FileStorageService fileStorageService,
                                  UserService userService) {
        this.contentService = contentService;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
        
        // Cleanup expired tokens every 30 minutes
        scheduler.scheduleAtFixedRate(() -> {
            videoTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 30, 30, TimeUnit.MINUTES);
    }

    @PostMapping("/token/{fileId}")
    public ResponseEntity<Map<String, String>> generateVideoToken(
            @PathVariable Long fileId,
            Authentication authentication) {
        
        try {
            User user = userService.findByUsername(authentication.getName());
            FileMetadata metadata = contentService.getFileMetadataById(fileId);
            
            // بررسی که فایل ویدیو باشه
            if (!metadata.getContentType().startsWith("video/")) {
                return ResponseEntity.badRequest().build();
            }
            
            // TODO: Add access control check here
            
            // ایجاد token
            String token = UUID.randomUUID().toString();
            videoTokens.put(token, new VideoTokenInfo(fileId, user.getId()));
            
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            response.put("streamUrl", "/api/video/stream/" + token);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/stream/{token}")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @PathVariable String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        VideoTokenInfo tokenInfo = videoTokens.get(token);
        
        if (tokenInfo == null || tokenInfo.isExpired()) {
            videoTokens.remove(token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            FileMetadata metadata = contentService.getFileMetadataById(tokenInfo.getFileId());
            Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());
            
            File videoFile = resource.getFile();
            long fileSize = videoFile.length();
            
            String rangeHeader = request.getHeader("Range");
            
            if (rangeHeader == null) {
                // کل فایل
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
                response.setContentType(metadata.getContentType());
                
                StreamingResponseBody responseBody = outputStream -> {
                    try (FileInputStream fileInputStream = new FileInputStream(videoFile);
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                };
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(metadata.getContentType()))
                        .body(responseBody);
            }
            
            // Range Request
            return handleRangeRequest(videoFile, metadata, rangeHeader, fileSize, response);
            
        } catch (Exception e) {
            System.err.println("Error streaming video: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private ResponseEntity<StreamingResponseBody> handleRangeRequest(
            File videoFile, FileMetadata metadata, String rangeHeader, 
            long fileSize, HttpServletResponse response) {
        
        try {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            long start = Long.parseLong(ranges[0]);
            long end = ranges.length > 1 && !ranges[1].isEmpty()
                    ? Long.parseLong(ranges[1])
                    : fileSize - 1;
            
            // بررسی range معتبر
            if (start >= fileSize || end >= fileSize || start > end) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                return ResponseEntity.status(416).build(); // Range Not Satisfiable
            }
            
            long contentLength = end - start + 1;
            
            // Set headers
            response.setStatus(206); // Partial Content
            response.setHeader(HttpHeaders.CONTENT_TYPE, metadata.getContentType());
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            response.setHeader(HttpHeaders.CONTENT_RANGE, 
                    String.format("bytes %d-%d/%d", start, end, fileSize));
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            
            StreamingResponseBody responseBody = outputStream -> {
                try (RandomAccessFile randomAccessFile = new RandomAccessFile(videoFile, "r")) {
                    randomAccessFile.seek(start);
                    
                    byte[] buffer = new byte[8192];
                    long totalBytesToRead = contentLength;
                    long totalBytesRead = 0;
                    
                    while (totalBytesRead < totalBytesToRead) {
                        long remainingBytes = totalBytesToRead - totalBytesRead;
                        int bytesToRead = (int) Math.min(buffer.length, remainingBytes);
                        
                        int bytesRead = randomAccessFile.read(buffer, 0, bytesToRead);
                        if (bytesRead == -1) break;
                        
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                    }
                    
                    outputStream.flush();
                    
                } catch (IOException e) {
                    System.err.println("Error in range streaming: " + e.getMessage());
                    throw e;
                }
            };
            
            return ResponseEntity.status(206)
                    .body(responseBody);
            
        } catch (Exception e) {
            System.err.println("Error handling range request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private static class VideoTokenInfo {
        private final Long fileId;
        private final Long userId;
        private final long createdAt;
        private volatile long lastAccessed;
        
        public VideoTokenInfo(Long fileId, Long userId) {
            this.fileId = fileId;
            this.userId = userId;
            this.createdAt = System.currentTimeMillis();
            this.lastAccessed = this.createdAt;
        }
        
        public boolean isExpired() {
            long now = System.currentTimeMillis();
            return now - createdAt > TimeUnit.HOURS.toMillis(2);
        }
        
        public Long getFileId() { return fileId; }
        public Long getUserId() { return userId; }
    }
}