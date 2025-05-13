// src/main/java/com/example/demo/repository/FileMetadataRepository.java
package com.example.demo.repository;

import com.example.demo.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    // Basic CRUD methods are inherited from JpaRepository
}