package com.example.demo.dto;

import lombok.Data;

@Data
public class AnswerDTO {
    private Long id;
    private String text;
    private Boolean correct;
    private String answerType;
    private String mediaUrl;
    private Integer points;
    private String feedback;
    private Integer orderIndex;
    private String category;
}