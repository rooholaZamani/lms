package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class BlankAnswerDTO {
    private Integer blankIndex;
    private String correctAnswer;
    private List<String> acceptableAnswers;
    private Boolean caseSensitive = false;
    private Integer points;
}