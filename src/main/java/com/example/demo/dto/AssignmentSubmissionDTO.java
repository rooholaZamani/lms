package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AssignmentSubmissionDTO {
    private Long id;
    private Long assignmentId;
    private String assignmentTitle;
    private Long studentId;
    private String studentName;
    private String comment;
    private FileMetadataDTO file;
    private LocalDateTime submittedAt;
    private Integer score;
    private String feedback;
    private boolean graded;
    private LocalDateTime gradedAt;
    private int totalStudents;
    private int submittedCount;
    private List<AssignmentSubmissionDTO> submissions;
}