package com.example.demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class ExamDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer passingScore;
    private List<QuestionDTO> questions = new ArrayList<>();
}
