package com.example.demo.dto;

public class LessonSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private Integer orderIndex;
    private boolean hasExam;
    private boolean hasExercise;

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

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public boolean isHasExam() {
        return hasExam;
    }

    public void setHasExam(boolean hasExam) {
        this.hasExam = hasExam;
    }

    public boolean isHasExercise() {
        return hasExercise;
    }

    public void setHasExercise(boolean hasExercise) {
        this.hasExercise = hasExercise;
    }
}