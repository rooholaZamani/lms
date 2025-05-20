package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
public class ProgressDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long courseId;
    private String courseTitle;
    private Set<Long> completedLessons = new HashSet<>();
    private Set<Long> viewedContent = new HashSet<>();
    private LocalDateTime lastAccessed;
    private Integer totalLessons;
    private Integer completedLessonCount;
    private Double completionPercentage;
}