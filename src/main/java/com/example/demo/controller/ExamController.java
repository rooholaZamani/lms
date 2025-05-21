package com.example.demo.controller;

import com.example.demo.dto.ExamDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.dto.SubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.ExamService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;

    public ExamController(
            ExamService examService,
            UserService userService,
            DTOMapperService dtoMapperService) {
        this.examService = examService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<ExamDTO> createExam(
            @PathVariable Long lessonId,
            @RequestBody Exam exam,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Exam savedExam = examService.createExam(exam, lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToExamDTO(savedExam));
    }
    @PostMapping("/{examId}/questions/clone/{questionId}")
    public ResponseEntity<QuestionDTO> cloneQuestionToExam(
            @PathVariable Long examId,
            @PathVariable Long questionId,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Question clonedQuestion = examService.cloneQuestionFromBank(examId, questionId);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(clonedQuestion));
    }

    @GetMapping("/{examId}")
    public ResponseEntity<ExamDTO> getExam(
            @PathVariable Long examId) {

        Exam exam = examService.getExamById(examId);
        return ResponseEntity.ok(dtoMapperService.mapToExamDTO(exam));
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