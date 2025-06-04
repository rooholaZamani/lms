package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Update LessonDTO.java to include new fields
@Data
public class LessonDTO {
    private Long id;
    private String title;
    private String description;
    private Integer orderIndex;
    private Integer duration; // Add this field
    private LocalDateTime createdAt; // Add this field
    private Long courseId;
    private String courseTitle;
    private List<ContentDTO> contents = new ArrayList<>();
    private ExamDTO exam;
    private ExerciseDTO exercise;
    private boolean hasExam;
    private boolean hasExercise;
    private CourseSummaryDTO course;

    @Data
    public static class CourseSummaryDTO {
        private Long id;
        private String title;
    }
}
