package com.example.demo.repository;

import com.example.demo.model.Assignment;
import com.example.demo.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByLesson(Lesson lesson);
    List<Assignment> findByLessonId(Long lessonId);
}