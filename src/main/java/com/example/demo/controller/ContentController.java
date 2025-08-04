package com.example.demo.controller;

import com.example.demo.dto.ContentDTO;
import com.example.demo.dto.ContentDetailsDTO;
import com.example.demo.model.*;
import com.example.demo.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import org.apache.commons.io.input.BoundedInputStream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;
    private final UserService userService;
    private final ActivityTrackingService activityTrackingService;
    private final LessonCompletionService lessonCompletionService;
    private final ProgressService progressService;


    public ContentController(
            ContentService contentService,
            LessonService lessonService,
            FileStorageService fileStorageService,
            DTOMapperService dtoMapperService,
            UserService userService, ActivityTrackingService activityTrackingService, LessonCompletionService lessonCompletionService, ProgressService progressService) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
        this.userService = userService;
        this.activityTrackingService = activityTrackingService;
        this.lessonCompletionService = lessonCompletionService;
        this.progressService = progressService;
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<?> getContentById(
            @PathVariable Long contentId,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // ADD THIS
            Authentication authentication) {

        try {
            Content content = contentService.getContentById(contentId);
            User currentUser = userService.findByUsername(authentication.getName());

            if (!hasAccessToContent(currentUser, content)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "You don't have permission to access this content");
                return ResponseEntity.status(403).body(errorResponse);
            }

            // ADD ACTIVITY TRACKING

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contentType", content.getType().toString()); // TEXT, VIDEO, PDF
            metadata.put("contentTitle", content.getTitle());
            metadata.put("lessonId", content.getLesson().getId().toString());
            metadata.put("lessonTitle", content.getLesson().getTitle());

            activityTrackingService.logActivity(currentUser, "CONTENT_VIEW", contentId, timeSpent,metadata);
            if (timeSpent > 0) {
                activityTrackingService.updateStudyTime(currentUser, timeSpent);
            }

//            ContentDetailsDTO contentDetails = dtoMapperService.mapToContentDetailsDTO(content);
            ContentDetailsDTO contentDetails = dtoMapperService.mapToContentDetailsDTO(content, currentUser);
            return ResponseEntity.ok(contentDetails);

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Content not found with ID: " + contentId);
                return ResponseEntity.status(404).body(errorResponse);
            }
            throw e;
        }
    }

    /**
     * Check if user has access to the content
     */
    private boolean hasAccessToContent(User user, Content content) {
        // Admin users have access to everything
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        Course course = content.getLesson().getCourse();

        // Teachers have access to their own course content
        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));
        if (isTeacher && course.getTeacher().getId().equals(user.getId())) {
            return true;
        }

        // Students have access if they're enrolled in the course
        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));
        if (isStudent && course.getEnrolledStudents().contains(user)) {
            return true;
        }

        return false;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "آپلود محتوای فایل", description = "آپلود یک فایل به عنوان محتوا درس")
    public ResponseEntity<ContentDTO> uploadContent(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lessonId") Long lessonId,
            @RequestParam("title") String title,
            @RequestParam("contentType") ContentType contentType,
            @RequestParam("orderIndex") Integer orderIndex){

        Lesson lesson = lessonService.getLessonById(lessonId);

        // Create content with file
        Content content = new Content();
        content.setTitle(title);
        content.setType(contentType);
        content.setLesson(lesson);
        content.setOrderIndex(orderIndex);

        // Generate path based on course and lesson ID
        String subdirectory = fileStorageService.generatePath(
                lesson.getCourse().getId(),
                lessonId,
                contentType.toString().toLowerCase());

        // Store file and get metadata
        FileMetadata metadata = fileStorageService.storeFile(file, subdirectory);
        content.setFile(metadata);

        Content savedContent = contentService.saveContent(content);
        return ResponseEntity.ok(dtoMapperService.mapToContentDTO(savedContent));
    }

    @PostMapping("/text")
    public ResponseEntity<ContentDTO> createTextContent(
            @RequestParam("lessonId") Long lessonId,
            @RequestParam("title") String title,
            @RequestParam("textContent") String textContent,
            @RequestParam("orderIndex") Integer orderIndex) {

        Lesson lesson = lessonService.getLessonById(lessonId);

        Content content = new Content();
        content.setTitle(title);
        content.setType(ContentType.TEXT);
        content.setTextContent(textContent);
        content.setLesson(lesson);
        content.setOrderIndex(orderIndex);

        Content savedContent = contentService.saveContent(content);
        return ResponseEntity.ok(dtoMapperService.mapToContentDTO(savedContent));
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<List<ContentDTO>> getLessonContent(@PathVariable Long lessonId) {
        List<Content> contents = contentService.getLessonContents(lessonId);
        List<ContentDTO> contentDTOs = contents.stream()
                .map(content -> dtoMapperService.mapToContentDTO(content))
                .collect(Collectors.toList());
        return ResponseEntity.ok(contentDTOs);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long fileId,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent,
            Authentication authentication,
            HttpServletRequest request) throws IOException {

        // Activity tracking همان کد قبلی...
        if (authentication != null) {
            User user = userService.findByUsername(authentication.getName());
            FileMetadata metadata = contentService.getFileMetadataById(fileId);

            Map<String, String> fileMetadata = new HashMap<>();
            fileMetadata.put("fileName", metadata.getOriginalFilename());
            fileMetadata.put("fileType", metadata.getContentType());
            fileMetadata.put("fileSize", metadata.getFileSize().toString());

            activityTrackingService.logActivity(user, "FILE_ACCESS", fileId, timeSpent, fileMetadata);
        }

        FileMetadata metadata = contentService.getFileMetadataById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());

        // برای ویدیو، از Range Request پشتیبانی کن
        if (metadata.getContentType().startsWith("video/")) {
            return handleVideoStreaming(request, resource, metadata);
        }

        // برای سایر فایل‌ها، همان روش قبلی
        String contentType = metadata.getContentType();
        String disposition = "attachment; filename=\"" + metadata.getOriginalFilename() + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }


    private ResponseEntity<Resource> handleVideoStreaming(
            HttpServletRequest request, Resource resource, FileMetadata metadata) throws IOException {

        long fileSize = resource.contentLength();
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null) {
            // اگر Range header نباشد، کل فایل را بفرست
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + metadata.getOriginalFilename() + "\"")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                    .body(resource);
        }

        // پردازش Range Request
        try {
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            long start = Long.parseLong(ranges[0]);
            long end = ranges.length > 1 && !ranges[1].isEmpty()
                    ? Long.parseLong(ranges[1])
                    : Math.min(start + 1024 * 1024, fileSize - 1); // محدود کردن chunk size

            // بررسی که range معتبر باشه
            if (start >= fileSize || end >= fileSize || start > end) {
                return ResponseEntity.status(416) // Range Not Satisfiable
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }

            long contentLength = end - start + 1;

            // استفاده از RandomAccessFile برای بهتر handle کردن
            if (resource instanceof FileSystemResource) {
                FileSystemResource fileResource = (FileSystemResource) resource;
                Path filePath = fileResource.getFile().toPath();

                Resource partialResource = new Resource() {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        RandomAccessFile randomAccessFile = new RandomAccessFile(fileResource.getFile(), "r");
                        randomAccessFile.seek(start);

                        return new InputStream() {
                            private long bytesRead = 0;
                            private final long maxBytes = contentLength;

                            @Override
                            public int read() throws IOException {
                                if (bytesRead >= maxBytes) {
                                    return -1;
                                }
                                int data = randomAccessFile.read();
                                if (data != -1) {
                                    bytesRead++;
                                }
                                return data;
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                if (bytesRead >= maxBytes) {
                                    return -1;
                                }
                                long remainingBytes = maxBytes - bytesRead;
                                int toRead = (int) Math.min(len, remainingBytes);
                                int actualRead = randomAccessFile.read(b, off, toRead);
                                if (actualRead > 0) {
                                    bytesRead += actualRead;
                                }
                                return actualRead;
                            }

                            @Override
                            public void close() throws IOException {
                                try {
                                    randomAccessFile.close();
                                } catch (IOException e) {
                                    // Log but don't throw
                                    System.err.println("Error closing RandomAccessFile: " + e.getMessage());
                                }
                            }
                        };
                    }

                    @Override
                    public boolean exists() { return fileResource.exists(); }

                    @Override
                    public URL getURL() throws IOException { return fileResource.getURL(); }

                    @Override
                    public URI getURI() throws IOException { return fileResource.getURI(); }

                    @Override
                    public File getFile() throws IOException { return fileResource.getFile(); }

                    @Override
                    public long contentLength() { return contentLength; }

                    @Override
                    public long lastModified() throws IOException { return fileResource.lastModified(); }

                    @Override
                    public Resource createRelative(String relativePath) throws IOException {
                        return fileResource.createRelative(relativePath);
                    }

                    @Override
                    public String getFilename() { return metadata.getOriginalFilename(); }

                    @Override
                    public String getDescription() { return "Partial video resource"; }
                };

                return ResponseEntity.status(206) // Partial Content
                        .contentType(MediaType.parseMediaType(metadata.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + metadata.getOriginalFilename() + "\"")
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE,
                                String.format("bytes %d-%d/%d", start, end, fileSize))
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .header("Connection", "keep-alive")
                        .body(partialResource);
            }

        } catch (NumberFormatException e) {
            return ResponseEntity.status(400).build();
        } catch (Exception e) {
            System.err.println("Error in handleVideoStreaming: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }

        // Fallback
        return ResponseEntity.status(500).build();
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<?> deleteContent(@PathVariable Long contentId) {
        contentService.deleteContent(contentId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/lesson/{lessonId}")
    @Operation(summary = "Update lesson contents", description = "Update all contents of a lesson")
    public ResponseEntity<List<ContentDTO>> updateLessonContents(
            @PathVariable Long lessonId,
            @RequestBody List<Content> contents) {

        // خواندن درس از دیتابیس
        Lesson lesson = lessonService.getLessonById(lessonId);

        // آپدیت کردن محتوا
        for (Content content : contents) {
            content.setLesson(lesson);
            contentService.saveContent(content);
        }

        // خواندن محتوای به‌روزرسانی شده
        List<Content> updatedContents = contentService.getLessonContents(lessonId);
        List<ContentDTO> contentDTOs = updatedContents.stream()
                .map(dtoMapperService::mapToContentDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(contentDTOs);
    }

    @PostMapping("/files/{fileId}/video-url")
    @Operation(summary = "Get video streaming URL")
    public ResponseEntity<Map<String, String>> getVideoUrl(
            @PathVariable Long fileId,
            Authentication authentication) {

        User user = userService.findByUsername(authentication.getName());

        // بررسی دسترسی
        Content content = contentService.getContentByFileId(fileId);
        if (!hasAccessToContent(user, content)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("videoUrl", "/video/token/" + fileId);

        return ResponseEntity.ok(response);
    }

}