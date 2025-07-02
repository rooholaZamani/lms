// src/main/java/com/example/demo/model/Course.java
package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String prerequisite; // Course prerequisite as text


    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean active = true;

    @ManyToOne
    private User teacher;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "course_enrollments",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> enrolledStudents = new ArrayList<>();

    // Helper method to calculate total duration
    public Integer getTotalDuration() {
        return lessons.stream()
                .mapToInt(lesson -> lesson.getDuration() != null ? lesson.getDuration() : 0)
                .sum();
    }
}