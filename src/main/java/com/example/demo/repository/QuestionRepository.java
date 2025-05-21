// src/main/java/com/example/demo/repository/QuestionRepository.java
package com.example.demo.repository;

import com.example.demo.model.Exam;
import com.example.demo.model.Exercise;
import com.example.demo.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByExamOrderById(Exam exam);
    List<Question> findByExerciseOrderById(Exercise exercise);
    List<Question> findByInBankTrue();
}