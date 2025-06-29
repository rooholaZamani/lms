package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LessonDTO {
    private Long id;
    private String title;
    private String description;
    private Integer orderIndex;
    private Integer duration = 0;
    private LocalDateTime createdAt;
    private Long courseId;
    private String courseTitle;
    private List<ContentDTO> contents = new ArrayList<>();
    private ExamDTO exam;
    private boolean hasExam;
    private boolean hasAssignment; // Changed from hasExercise to hasAssignment
    private CourseSummaryDTO course;

    @Data
    public static class CourseSummaryDTO {
        private Long id;
        private String title;
    }
}