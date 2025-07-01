package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExamDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit; // Time limit  in seconds
    private Integer passingScore;
    
    // New finalization fields
    private String status; // ExamStatus as string
    private LocalDateTime finalizedAt;
    private String finalizedBy; // Teacher name who finalized
    private Integer totalPossibleScore;
    private LocalDateTime availableFrom;
    private LocalDateTime availableTo;

    private boolean canBeDeleted;
    
    // Lesson information
    private Long lessonId;
    private String lessonTitle;

    private boolean hasStudentTaken; // آیا دانش‌آموز قبلاً شرکت کرده
    private Integer studentScore; // نمره دانش‌آموز (اگر شرکت کرده)
    private boolean studentPassed; // آیا دانش‌آموز قبول شده
    private LocalDateTime studentSubmissionTime; // زمان شرکت دانش‌آموز
    
    // Questions (only included when specifically requested)
    private List<QuestionDTO> questions = new ArrayList<>();
    
    // Additional metadata
    private Integer questionCount;
    private boolean canBeModified;
    private boolean AvailableForStudents;
}