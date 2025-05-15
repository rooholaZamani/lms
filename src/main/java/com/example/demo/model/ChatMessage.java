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
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    private Course course;

    @ManyToOne
    private User sender;

    @Column(length = 2000)
    private String content;

    private LocalDateTime sentAt;

    @ElementCollection
    @CollectionTable(name = "read_messages",
            joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "user_id")
    private Set<Long> readBy = new HashSet<>();
}