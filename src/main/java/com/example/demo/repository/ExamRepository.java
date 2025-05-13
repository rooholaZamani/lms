// src/main/java/com/example/demo/repository/ExamRepository.java
package com.example.demo.repository;

import com.example.demo.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByLessonId(Long lessonId);
}