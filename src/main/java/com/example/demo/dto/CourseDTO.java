package com.example.demo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class CourseDTO {
    private Long id;
    private String title;
    private String description;
    private UserSummaryDTO teacher;
    private List<LessonSummaryDTO> lessons = new ArrayList<>();
    private List<UserSummaryDTO> enrolledStudents = new ArrayList<>();
}