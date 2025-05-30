package com.example.demo.dto;

import lombok.Data;

@Data
public class CourseDetailsDTO {
    private CourseDTO course;
    private ProgressDTO progress; // Only included for students
    private Boolean isTeacher;
    private Boolean isStudent;
}