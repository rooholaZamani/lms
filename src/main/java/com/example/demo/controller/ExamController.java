package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.service.ExamService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@CrossOrigin(origins = "*")
public class ExamController {

    private final ExamService examService;
    private final UserService userService;

    public ExamController(
            ExamService examService,
            UserService userService) {
        this.examService = examService;
        this.userService = userService;
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<Exam> createExam(
            @PathVariable Long lessonId,
            @RequestBody Exam exam,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Exam savedExam = examService.createExam(exam, lessonId);
        return ResponseEntity.ok(savedExam);
    }

    @GetMapping("/{examId}")
    public ResponseEntity<Exam> getExam(
            @PathVariable Long examId) {

        Exam exam = examService.getExamById(examId);
        return ResponseEntity.ok(exam);
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<Exam> getExamByLesson(
            @PathVariable Long lessonId) {

        Exam exam = examService.getExamByLessonId(lessonId);
        return ResponseEntity.ok(exam);
    }

    @PostMapping("/{examId}/questions")
    public ResponseEntity<Question> addQuestion(
            @PathVariable Long examId,
            @RequestBody Question question,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Question savedQuestion = examService.addQuestion(question, examId);
        return ResponseEntity.ok(savedQuestion);
    }

    @GetMapping("/{examId}/questions")
    public ResponseEntity<List<Question>> getExamQuestions(
            @PathVariable Long examId) {

        List<Question> questions = examService.getExamQuestions(examId);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/{examId}/submit")
    public ResponseEntity<Submission> submitExam(
            @PathVariable Long examId,
            @RequestBody Map<Long, Long> answers,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        Submission submission = examService.submitExam(examId, student, answers);
        return ResponseEntity.ok(submission);
    }

    @GetMapping("/submissions/student")
    public ResponseEntity<List<Submission>> getStudentSubmissions(
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        List<Submission> submissions = examService.getStudentSubmissions(student);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{examId}/submissions")
    public ResponseEntity<List<Submission>> getExamSubmissions(
            @PathVariable Long examId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<Submission> submissions = examService.getExamSubmissions(examId);
        return ResponseEntity.ok(submissions);
    }
}