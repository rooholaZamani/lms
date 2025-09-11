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
    private Set<Long> completedContent = new HashSet<>();
    
    // Additional fields for detailed progress tracking
    private Integer totalContentCount;
    private Integer viewedContentCount;
    private Integer totalAssignmentCount;
    private Integer submittedAssignmentCount;
    private Integer totalExamCount;
    private Integer attemptedExamCount;
}