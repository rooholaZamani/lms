// src/main/java/com/example/demo/repository/SubmissionRepository.java
package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.Exam;
import com.example.demo.model.Submission;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByStudent(User student);
    List<Submission> findByExam(Exam exam);
    Optional<Submission> findByStudentAndExam(User student, Exam exam);
    List<Submission> findByStudentAndSubmissionTimeBetween(User student, LocalDateTime start, LocalDateTime end);

    List<Submission> findBySubmissionTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Submission> findByExamAndSubmissionTimeBetween(Exam exam, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s FROM Submission s WHERE s.exam.lesson.course = :course AND s.submissionTime BETWEEN :start AND :end")
    List<Submission> findByTimestampBetweenAndExam_Course(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("course") Course course);

    @Query("SELECT s FROM Submission s WHERE s.student = :student AND s.submissionTime BETWEEN :start AND :end")
    List<Submission> findByStudentAndTimestampBetween(@Param("student") User student, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Modifying
    @Transactional
    @Query("DELETE FROM Submission s WHERE s.exam = :exam")
    void deleteByExam(@Param("exam") Exam exam);

    @Query("SELECT s FROM Submission s WHERE s.exam.id = :examId AND s.student = :student")
    Optional<Submission> findByExamIdAndStudent(@Param("examId") Long examId, @Param("student") User student);

    // Optimized query methods to avoid stream().filter()
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.exam = :exam AND s.passed = true")
    long countByExamAndPassedTrue(@Param("exam") Exam exam);

    @Query("SELECT s FROM Submission s WHERE s.exam = :exam AND s.passed = true")
    List<Submission> findByExamAndPassedTrue(@Param("exam") Exam exam);

}