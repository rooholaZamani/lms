package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Progress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private User student;

    @ManyToOne
    private Course course;

    // Track completed lessons
    @ElementCollection
    @CollectionTable(name = "completed_lessons",
            joinColumns = @JoinColumn(name = "progress_id"))
    @Column(name = "lesson_id")
    private Set<Long> completedLessons = new HashSet<>();

    // Track viewed content
    @ElementCollection
    @CollectionTable(name = "viewed_content",
            joinColumns = @JoinColumn(name = "progress_id"))
    @Column(name = "content_id")
    private Set<Long> viewedContent = new HashSet<>();

    private LocalDateTime lastAccessed;

    // Cache calculated values for performance
    private Integer totalLessons;
    private Integer completedLessonCount;
    private Double completionPercentage;
    @ElementCollection
    @CollectionTable(name = "completed_content",
            joinColumns = @JoinColumn(name = "progress_id"))
    @Column(name = "content_id")
    private Set<Long> completedContent = new HashSet<>();
}