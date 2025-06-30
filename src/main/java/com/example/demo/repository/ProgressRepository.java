package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends JpaRepository<Progress, Long> {
    List<Progress> findByStudent(User student);
    Optional<Progress> findByStudentAndCourse(User student, Course course);
    List<Progress> findByCourse(Course course);
}