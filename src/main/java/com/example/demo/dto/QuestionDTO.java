package com.example.demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionDTO {
    private Long id;
    private String text;
    private Integer points;
    private List<AnswerDTO> answers = new ArrayList<>();
}
