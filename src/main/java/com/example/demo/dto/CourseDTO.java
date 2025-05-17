package com.example.demo.dto;

import java.util.ArrayList;
import java.util.List;

public class CourseDTO {
    private Long id;
    private String title;
    private String description;
    private UserSummaryDTO teacher;
    private List<LessonSummaryDTO> lessons = new ArrayList<>();
    private List<UserSummaryDTO> enrolledStudents = new ArrayList<>();

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UserSummaryDTO getTeacher() {
        return teacher;
    }

    public void setTeacher(UserSummaryDTO teacher) {
        this.teacher = teacher;
    }

    public List<LessonSummaryDTO> getLessons() {
        return lessons;
    }

    public void setLessons(List<LessonSummaryDTO> lessons) {
        this.lessons = lessons;
    }

    public List<UserSummaryDTO> getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(List<UserSummaryDTO> enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }
}