package com.example.demo.dto;

import com.example.demo.model.ContentType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
@Data
public class LessonDTO {
    private Long id;
    private String title;
    private String description;
    private Integer orderIndex;
    private Long courseId;
    private String courseTitle;
    private List<ContentDTO> contents = new ArrayList<>();
    private ExamDTO exam;
    private ExerciseDTO exercise;
    private boolean hasExam;
    private boolean hasExercise;
}