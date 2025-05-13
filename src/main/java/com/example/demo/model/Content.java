package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}