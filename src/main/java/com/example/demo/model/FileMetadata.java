package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String originalFilename;
    private String storedFilename; // Unique name in the file system
    private String filePath;       // Relative path in storage
    private String contentType;    // MIME type
    private Long fileSize;         // Size in bytes
}