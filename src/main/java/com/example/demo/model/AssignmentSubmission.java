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
public class AssignmentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private Assignment assignment;

    @ManyToOne
    private User student;

    @Column(length = 2000)
    private String comment;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private FileMetadata file;

    private LocalDateTime submittedAt;

    private Integer score;

    private String feedback;

    private boolean graded;

    private LocalDateTime gradedAt;
}