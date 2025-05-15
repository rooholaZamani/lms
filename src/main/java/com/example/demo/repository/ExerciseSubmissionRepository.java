package com.example.demo.repository;

import com.example.demo.model.ExerciseSubmission;
import com.example.demo.model.Exercise;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExerciseSubmissionRepository extends JpaRepository<ExerciseSubmission, Long> {
    List<ExerciseSubmission> findByStudent(User student);
    List<ExerciseSubmission> findByExercise(Exercise exercise);
}