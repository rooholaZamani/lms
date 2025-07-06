package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private User student;

    @ManyToOne
    private Exam exam;

    private LocalDateTime submissionTime;

    private Integer score;

    private boolean passed;

    // Store answers as JSON string to support different question types
    @Lob
    @Column(columnDefinition = "TEXT")
    private String answersJson;

    private Long timeSpent; // Time spent on exam in seconds
}