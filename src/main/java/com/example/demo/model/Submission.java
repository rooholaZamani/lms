package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    // Store question ID -> answer ID mapping
    @ElementCollection
    @CollectionTable(name = "submission_answers",
            joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_id")
    @Column(name = "answer_id")
    private Map<Long, Long> answers = new HashMap<>();
    private Long timeSpent; // Time spent on activity in seconds // time spent on exam in seconds


}