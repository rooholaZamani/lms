package com.example.demo.controller;

import com.example.demo.dto.ExamDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.dto.SubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.ExamService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams", description = "Exam management operations")
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
    @Operation(summary = "Create a new exam", description = "Create a new exam for a specific lesson")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<ExamDTO> createExam(
            @PathVariable Long lessonId,
            @RequestBody Exam exam,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Exam savedExam = examService.createExam(exam, lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToExamDTO(savedExam));
    }

    @PostMapping("/{examId}/questions/clone/{questionId}")
    @Operation(summary = "Clone question from bank", description = "Clone a question from question bank to exam")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<QuestionDTO> cloneQuestionToExam(
            @PathVariable Long examId,
            @PathVariable Long questionId,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Question clonedQuestion = examService.cloneQuestionFromBank(examId, questionId);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(clonedQuestion));
    }

    @GetMapping("/{examId}")
    @Operation(summary = "Get exam details", description = "Retrieve detailed information about an exam")
    public ResponseEntity<ExamDTO> getExam(@PathVariable Long examId) {
        Exam exam = examService.getExamById(examId);
        return ResponseEntity.ok(dtoMapperService.mapToExamDTO(exam));
    }

    // 🔥 FIX: این endpoint حالا از DTO استفاده می‌کند
    @GetMapping("/lesson/{lessonId}")
    @Operation(summary = "Get exam by lesson", description = "Retrieve exam associated with a specific lesson")
    public ResponseEntity<ExamDTO> getExamByLesson(@PathVariable Long lessonId) {
        Exam exam = examService.getExamByLessonId(lessonId);
        ExamDTO examDTO = dtoMapperService.mapToExamDTO(exam);
        return ResponseEntity.ok(examDTO);
    }

    @PostMapping("/{examId}/questions")
    @Operation(summary = "Add question to exam", description = "Add a new question to an existing exam")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<QuestionDTO> addQuestion(
            @PathVariable Long examId,
            @RequestBody Question question,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Question savedQuestion = examService.addQuestion(question, examId);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(savedQuestion));
    }

    @GetMapping("/{examId}/questions")
    @Operation(summary = "Get exam questions", description = "Retrieve all questions for a specific exam")
    public ResponseEntity<List<QuestionDTO>> getExamQuestions(@PathVariable Long examId) {
        List<Question> questions = examService.getExamQuestions(examId);
        List<QuestionDTO> questionDTOs = questions.stream()
                .map(dtoMapperService::mapToQuestionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(questionDTOs);
    }

    @PostMapping("/{examId}/submit")
    @Operation(summary = "Submit exam", description = "Submit student answers for an exam")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<SubmissionDTO> submitExam(
            @PathVariable Long examId,
            @RequestBody Map<Long, Long> answers,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        Submission submission = examService.submitExam(examId, student, answers);
        return ResponseEntity.ok(dtoMapperService.mapToSubmissionDTO(submission));
    }

    @GetMapping("/submissions/student")
    @Operation(summary = "Get student submissions", description = "Retrieve all exam submissions for current student")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<SubmissionDTO>> getStudentSubmissions(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Submission> submissions = examService.getStudentSubmissions(student);
        List<SubmissionDTO> submissionDTOs = submissions.stream()
                .map(dtoMapperService::mapToSubmissionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(submissionDTOs);
    }

    @GetMapping("/{examId}/submissions")
    @Operation(summary = "Get exam submissions", description = "Retrieve all submissions for a specific exam (teacher only)")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<SubmissionDTO>> getExamSubmissions(
            @PathVariable Long examId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<Submission> submissions = examService.getExamSubmissions(examId);
        List<SubmissionDTO> submissionDTOs = submissions.stream()
                .map(dtoMapperService::mapToSubmissionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(submissionDTOs);
    }

    @PutMapping("/{examId}/finalize")
    @Operation(
        summary = "Finalize exam", 
        description = "Transform exam from draft to finalized status, making it available for students"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Exam successfully finalized"),
        @ApiResponse(responseCode = "400", description = "Exam cannot be finalized (validation failed)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
        @ApiResponse(responseCode = "403", description = "Forbidden - user doesn't own this exam"),
        @ApiResponse(responseCode = "404", description = "Exam not found")
    })
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> finalizeExam(
            @Parameter(description = "ID of the exam to finalize") 
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());
            
            // Finalize the exam
            Exam finalizedExam = examService.finalizeExam(examId, teacher);
            
            // Prepare success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Exam finalized successfully");
            response.put("examId", finalizedExam.getId());
            response.put("title", finalizedExam.getTitle());
            response.put("status", finalizedExam.getStatus().toString());
            response.put("totalPossibleScore", finalizedExam.getTotalPossibleScore());
            response.put("finalizedAt", finalizedExam.getFinalizedAt());
            response.put("availableFrom", finalizedExam.getAvailableFrom());
            
            // Include exam statistics
            List<Question> questions = examService.getExamQuestions(examId);
            response.put("questionCount", questions.size());
            response.put("passingScore", finalizedExam.getPassingScore());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            // Handle business logic errors
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("examId", examId);
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{examId}/finalization-info")
    @Operation(
        summary = "Get exam finalization information", 
        description = "Get information about whether exam can be finalized and validation details"
    )
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> getExamFinalizationInfo(
            @Parameter(description = "ID of the exam") 
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());
            Map<String, Object> info = examService.getExamFinalizationInfo(examId, teacher);
            return ResponseEntity.ok(info);
            
        } catch (RuntimeException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}