package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private User user;
    private String activityType; // LESSON_START, LESSON_COMPLETE, CONTENT_VIEW, etc.
    private Long relatedEntityId; // lesson/content/exam ID
    private LocalDateTime timestamp;
    private Long timeSpent; // in minutes
    private Map<String, Object> metadata; // additional data as JSON

}