// src/main/java/com/example/demo/service/ContentService.java
package com.example.demo.service;

import com.example.demo.model.Content;
import com.example.demo.model.FileMetadata;
import com.example.demo.repository.ContentRepository;
import com.example.demo.repository.FileMetadataRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public ContentService(
            ContentRepository contentRepository,
            FileMetadataRepository fileMetadataRepository) {
        this.contentRepository = contentRepository;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    public Content saveContent(Content content) {
        return contentRepository.save(content);
    }

    public List<Content> getLessonContents(Long lessonId) {
        return contentRepository.findByLessonIdOrderByOrderIndex(lessonId);
    }

    public Content getContentById(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found"));
    }

    public FileMetadata getFileMetadataById(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
    }

    public void deleteContent(Long contentId) {
        contentRepository.deleteById(contentId);
    }
}