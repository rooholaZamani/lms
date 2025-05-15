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
public class ExerciseSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private User student;

    @ManyToOne
    private Exercise exercise;

    private LocalDateTime submissionTime;

    private Integer score;

    private Integer timeBonus; // Additional points for fast answers

    private Integer totalScore;

    private boolean passed;

    // Store question ID -> answer ID mapping
    @ElementCollection
    @CollectionTable(name = "exercise_submission_answers",
            joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_id")
    @Column(name = "answer_id")
    private Map<Long, Long> answers = new HashMap<>();

    // Store question ID -> time taken (in seconds)
    @ElementCollection
    @CollectionTable(name = "exercise_answer_times",
            joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_id")
    @Column(name = "time_taken")
    private Map<Long, Integer> answerTimes = new HashMap<>();
}