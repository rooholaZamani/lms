package com.example.demo.dto;

import lombok.Data;

@Data
public class LessonSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private Integer orderIndex;
    private boolean hasExam;
    private boolean hasAssignment; // Changed from hasExercise to hasAssignment
}