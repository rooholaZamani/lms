package com.example.demo.dto;

import lombok.Data;

@Data
public class AnswerDTO {
    private Long id;
    private String text;
    private boolean correct;
}