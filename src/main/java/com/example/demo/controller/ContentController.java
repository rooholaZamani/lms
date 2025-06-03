package com.example.demo.controller;

import com.example.demo.dto.ContentDTO;
import com.example.demo.dto.ContentDetailsDTO;
import com.example.demo.model.*;
import com.example.demo.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;
    private final UserService userService;
    private final ActivityTrackingService activityTrackingService;

    public ContentController(
            ContentService contentService,
            LessonService lessonService,
            FileStorageService fileStorageService,
            DTOMapperService dtoMapperService,
            UserService userService, ActivityTrackingService activityTrackingService) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
        this.userService = userService;
        this.activityTrackingService = activityTrackingService;
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
            activityTrackingService.logActivity(currentUser, "CONTENT_VIEW", contentId, timeSpent);
            if (timeSpent > 0) {
                activityTrackingService.updateStudyTime(currentUser, timeSpent);
            }

            ContentDetailsDTO contentDetails = dtoMapperService.mapToContentDetailsDTO(content);
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
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // ADD THIS
            Authentication authentication, // ADD THIS
            HttpServletRequest request) {

        // ADD ACTIVITY TRACKING FOR FILE ACCESS
        if (authentication != null && timeSpent > 0) {
            User user = userService.findByUsername(authentication.getName());
            activityTrackingService.logActivity(user, "FILE_ACCESS", fileId, timeSpent);
            activityTrackingService.updateStudyTime(user, timeSpent);
        }

        FileMetadata metadata = contentService.getFileMetadataById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());

        // Try to determine content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // Logger could be used here
        }

        // Fallback to the content type stored in metadata
        if (contentType == null) {
            contentType = metadata.getContentType();
        }

        // For videos, use content-disposition: inline to enable streaming
        String disposition = metadata.getContentType().startsWith("video/") ?
                "inline; filename=\"" + metadata.getOriginalFilename() + "\"" :
                "attachment; filename=\"" + metadata.getOriginalFilename() + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
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
}