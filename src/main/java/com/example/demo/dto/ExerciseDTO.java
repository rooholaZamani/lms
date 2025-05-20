package com.example.demo.dto;


import lombok.Data;

@Data
public class ExerciseDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimit;
    private Integer passingScore;
    private Boolean adaptiveDifficulty;

}
