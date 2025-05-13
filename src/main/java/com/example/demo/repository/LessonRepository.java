// src/main/java/com/example/demo/repository/LessonRepository.java
package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseOrderByOrderIndex(Course course);
    List<Lesson> findByCourseIdOrderByOrderIndex(Long courseId);
}