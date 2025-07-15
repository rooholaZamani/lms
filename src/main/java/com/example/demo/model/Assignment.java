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
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne
    private Lesson lesson;

    @ManyToOne
    private User teacher;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
//    @JoinColumn(name = "file_id")
    private FileMetadata file;

    private LocalDateTime createdAt;

    private LocalDateTime dueDate;
}