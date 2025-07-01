package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    private Integer timeLimit; // in minutes, optional

    private Integer passingScore; // minimum score to pass

    // New fields for finalization
    @Enumerated(EnumType.STRING)
    private ExamStatus status = ExamStatus.DRAFT; // Default to DRAFT

    private LocalDateTime finalizedAt; // When the exam was finalized

    @ManyToOne
    private User finalizedBy; // Which teacher finalized the exam

    private Integer totalPossibleScore; // Calculated total score

    private LocalDateTime availableFrom; // When students can start taking the exam

    private LocalDateTime availableTo; // When exam becomes unavailable (optional)

    // Existing relationships
    @OneToOne(mappedBy = "exam")
    private Lesson lesson;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    // Helper method to check if exam can be modified
    public boolean canBeModified() {
        return this.status == ExamStatus.DRAFT;
    }

    // Helper method to check if exam is available for students
    public boolean isAvailableForStudents() {
        if (this.status != ExamStatus.FINALIZED) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Check if exam is within available time window
        if (availableFrom != null && now.isBefore(availableFrom)) {
            return false;
        }
        
        if (availableTo != null && now.isAfter(availableTo)) {
            return false;
        }
        
        return true;
    }
}