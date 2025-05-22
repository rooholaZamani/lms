package com.example.demo.dto;

import com.example.demo.model.QuestionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionDTO {
    private Long id;
    private String text;
    private QuestionType questionType;
    private Integer points;
    private String explanation;
    private String hint;
    private String template;
    private Integer timeLimit;
    private Boolean Required;
    private Double difficulty;


    private List<AnswerDTO> answers = new ArrayList<>();
}