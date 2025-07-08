package com.example.demo.dto;


import com.example.demo.model.Lesson;
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

    private Map<String, Object> answersDetails = new HashMap<>();

    private Integer timeLimit;
    private Long actualDuration;
    private String courseTitle;
    private Integer questionCount;
    private Integer totalPossibleScore;
    private String lessonTitle;
}