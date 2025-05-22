package com.example.demo.dto;

import lombok.Data;

@Data
public class AnswerOptionDTO {
    private String text;
    private Boolean correct = false;
    private String answerType = "TEXT"; // TEXT, IMAGE, AUDIO
    private String mediaUrl;
    private Integer points = 0;
    private String feedback;
    private Integer orderIndex;
    private String category; // برای دسته‌بندی
}