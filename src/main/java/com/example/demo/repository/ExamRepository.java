// src/main/java/com/example/demo/repository/ExamRepository.java
package com.example.demo.repository;

import com.example.demo.model.Exam;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByLessonId(Long lessonId);
    @Query("SELECT e FROM Exam e WHERE e.lesson.course.teacher = :teacher ORDER BY e.id DESC")
    List<Exam> findByTeacher(@Param("teacher") User teacher);
}