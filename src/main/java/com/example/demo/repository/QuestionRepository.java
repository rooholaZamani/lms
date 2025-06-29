package com.example.demo.repository;

import com.example.demo.model.Exam;
import com.example.demo.model.Question;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByExamOrderById(Exam exam);
    List<Question> findByInBankTrue();
    @Query("SELECT q FROM Question q WHERE q.exam.lesson.course.teacher = :teacher")
    List<Question> findByTeacher(@Param("teacher") User teacher);
}