package com.example.demo.dto;

import com.example.demo.model.ContentType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ContentDetailsDTO {
    private Long id;
    private String title;
    private ContentType type;
    private String textContent;
    private Long fileId;
    private String fileUrl;
    private Integer orderIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LessonInfo lesson;
    private Boolean isCompleted;
    private Boolean isViewed;
    private CompletionInfo completion;

    @Data
    public static class LessonInfo {
        private Long id;
        private String title;
        private Long courseId;
        private String courseTitle;
    }

    @Data
    public static class CompletionInfo {
        private Boolean isLessonCompleted;
        private Double lessonCompletionPercentage;
        private Integer totalLessonContents;
        private Integer completedLessonContents;
    }
}
