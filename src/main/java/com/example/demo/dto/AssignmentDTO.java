package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssignmentDTO {
    private Long id;
    private String title;
    private String description;
    private Long lessonId;
    private Long teacherId;
    private String teacherName;
    private FileMetadataDTO file;
    private LocalDateTime createdAt;
    private LocalDateTime dueDate;
}