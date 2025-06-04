// src/main/java/com/example/demo/dto/ExamWithDetailsDTO.java
package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExamWithDetailsDTO {
    private Long id;
    private String title;
    private String description;
    private Long lessonId;
    private LessonSummary lesson;
    private Integer duration = 0; // timeLimit
    private Integer passingScore;
    private String status;
    private LocalDateTime createdAt;
    private Integer questionCount;
    private List<SubmissionSummary> submissions = new ArrayList<>();

    @Data
    public static class LessonSummary {
        private Long id;
        private String title;
        private CourseSummary course;
    }

    @Data
    public static class CourseSummary {
        private Long id;
        private String title;
    }

    @Data
    public static class SubmissionSummary {
        private Long id;
        private Long studentId;
        private Integer score;
        private LocalDateTime submittedAt;
    }
}