package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ExerciseSubmissionDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long exerciseId;
    private String exerciseTitle;
    private LocalDateTime submissionTime;
    private Integer score;
    private Integer timeBonus;
    private Integer totalScore;
    private boolean passed;
    private Map<Long, Long> answers;
    private Map<Long, Integer> answerTimes;
}
