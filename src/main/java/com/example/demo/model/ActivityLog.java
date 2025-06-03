package com.example.demo.model;

import com.example.demo.model.User;
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
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne  // Add this annotation
    private User user;

    private String activityType;
    private Long relatedEntityId;
    private LocalDateTime timestamp;
    private Long timeSpent;

    @ElementCollection  // Add this for Map storage
    @CollectionTable(name = "activity_metadata")
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>(); // Change to String,String for simplicity
}