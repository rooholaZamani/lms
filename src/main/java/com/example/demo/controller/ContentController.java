package com.example.demo.controller;

import com.example.demo.dto.ContentDTO;
import com.example.demo.model.*;
import com.example.demo.service.ContentService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.LessonService;
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
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService contentService;
    private final LessonService lessonService;
    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;

    public ContentController(
            ContentService contentService,
            LessonService lessonService,
            FileStorageService fileStorageService,
            DTOMapperService dtoMapperService) {
        this.contentService = contentService;
        this.lessonService = lessonService;
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
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
            HttpServletRequest request) {

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
}