package com.example.demo.dto;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class SubmissionDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long examId;
    private String examTitle;
    private LocalDateTime submissionTime;
    private Integer score;
    private boolean passed;
    private Map<Long, Long> answers = new HashMap<>();
    private Integer timeLimit; // Time limit  in seconds
    private Long actualDuration;
    private String courseTitle;

    // Getters and setters
}