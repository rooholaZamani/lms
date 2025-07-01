package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private ContentType type;

    @Column(length = 4000)
    private String textContent; // Used for TEXT type

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private FileMetadata file; // Used for VIDEO and PDF types

    @ManyToOne
    private Lesson lesson;

    private Integer orderIndex; // For ordering content within a lesson
    @CreatedDate
    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;


    @LastModifiedDate
    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

    private Long avgViewTime; // Average viewing time in seconds (if tracked)

}