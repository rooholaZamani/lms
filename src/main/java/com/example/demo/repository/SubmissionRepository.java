// src/main/java/com/example/demo/repository/SubmissionRepository.java
package com.example.demo.repository;

import com.example.demo.model.Exam;
import com.example.demo.model.Submission;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudent(User student);
    List<Submission> findByExam(Exam exam);
    Optional<Submission> findByStudentAndExam(User student, Exam exam);
}