package com.example.demo.repository;

import com.example.demo.model.AssignmentSubmission;
import com.example.demo.model.Assignment;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    List<AssignmentSubmission> findByAssignment(Assignment assignment);
    List<AssignmentSubmission> findByStudent(User student);
    List<AssignmentSubmission> findByStudentAndSubmittedAtBetween(User student, LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM AssignmentSubmission a WHERE a.student = :student AND a.submittedAt BETWEEN :start AND :end")
    List<AssignmentSubmission> findByStudentAndSubmissionDateBetween(@Param("student") User student, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<AssignmentSubmission> findBySubmittedAtBetween(LocalDateTime start, LocalDateTime end);

    List<AssignmentSubmission> findByAssignmentAndSubmittedAtBetween(Assignment assignment, LocalDateTime start, LocalDateTime end);
}