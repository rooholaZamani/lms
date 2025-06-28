package com.example.demo.controller;

import com.example.demo.dto.ExamDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.dto.SubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.repository.SubmissionRepository;
import com.example.demo.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.example.demo.dto.ExamWithDetailsDTO;

@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams", description = "Exam management operations")
public class ExamController {

    private final ExamService examService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;
    private final ActivityTrackingService activityTrackingService;
    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;

    public ExamController(
            ExamService examService,
            UserService userService,
            DTOMapperService dtoMapperService, ActivityTrackingService activityTrackingService, SubmissionRepository submissionRepository, SubmissionService submissionService) {
        this.examService = examService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
        this.activityTrackingService = activityTrackingService;
        this.submissionRepository = submissionRepository;
        this.submissionService = submissionService;
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
    public ResponseEntity<ExamDTO> getExam(
            @PathVariable Long examId,
            Authentication authentication) { // ADD THIS

        // ADD ACTIVITY TRACKING FOR EXAM ACCESS
        User user = null;
        if (authentication != null) {
            user = userService.findByUsername(authentication.getName());
            activityTrackingService.logActivity(user, "EXAM_START", examId, 0L);
        }

        Exam exam = examService.getExamById(examId);
        ExamDTO examDTO = dtoMapperService.mapToExamDTO(exam, user);

        return ResponseEntity.ok(examDTO);
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
    @Transactional  // Add this annotation
    public ResponseEntity<SubmissionDTO> submitExam(
            @PathVariable Long examId,
            @RequestBody Map<String, Object> submissionData,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());

        if (examService.hasStudentTakenExam(examId, student)) {
            throw new RuntimeException("You have already submitted this exam");
        }
        // FIX: Convert String keys to Long keys
        Map<Long, Long> answers = new HashMap<>();
        Object answersObj = submissionData.get("answers");

        if (answersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> answersMap = (Map<String, Object>) answersObj;

            for (Map.Entry<String, Object> entry : answersMap.entrySet()) {
                Long questionId = Long.parseLong(entry.getKey());
                Long answerId = ((Number) entry.getValue()).longValue();
                answers.put(questionId, answerId);
            }
        }

        Long timeSpent = submissionData.get("timeSpent") != null ?
                ((Number) submissionData.get("timeSpent")).longValue() : 0L;

        // Submit exam with converted answers
        Submission submission = examService.submitExam(examId, student, answers);

        // Update submission with time spent and save again
//        submission.setTimeSpent(timeSpent);
        // You'll need to add this method to ExamService or save here
        submission = examService.updateSubmissionTimeSpent(submission, timeSpent);
        // ADD ACTIVITY TRACKING
        activityTrackingService.logActivity(student, "EXAM_SUBMISSION", examId, timeSpent);
        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }

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
    @GetMapping("/teaching")
    @Operation(
            summary = "Get teacher's exams",
            description = "Get all exams created by the authenticated teacher with lesson and submission details"
    )
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<ExamWithDetailsDTO>> getTeacherExams(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can access this endpoint");
        }

        // Get exams by teacher
        List<Exam> exams = examService.getExamsByTeacher(teacher);

        // Get submissions for these exams
        Map<Long, List<Submission>> submissionsByExam = examService.getSubmissionsByExamsForTeacher(teacher);

        // Convert to DTOs
        List<ExamWithDetailsDTO> examDTOs = dtoMapperService.mapToExamWithDetailsDTOList(exams, submissionsByExam);

        return ResponseEntity.ok(examDTOs);
    }
    @GetMapping("/available")
    @Operation(
            summary = "Get available exams",
            description = "Get all exams available for the authenticated student to take"
    )
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<ExamDTO>> getAvailableExams(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());

        // Verify user is a student
        boolean isStudent = student.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        if (!isStudent) {
            throw new RuntimeException("Access denied: Only students can access available exams");
        }

        List<Exam> availableExams = examService.getAvailableExamsForStudent(student);

        // Map to DTOs without question details for security
        List<ExamDTO> examDTOs = availableExams.stream()
                .map(dtoMapperService::mapToExamDTOWithoutQuestions)
                .collect(Collectors.toList());

        return ResponseEntity.ok(examDTOs);
    }
    @GetMapping("/{examId}/submission-status")
    @Operation(summary = "Check exam submission status", description = "Check if student has already taken the exam")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> getExamSubmissionStatus(
            @PathVariable Long examId,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());

        Map<String, Object> status = new HashMap<>();
        Optional<Submission> submission = examService.getStudentSubmission(examId, student);

        if (submission.isPresent()) {
            Submission sub = submission.get();
            status.put("hasTaken", true);
            status.put("score", sub.getScore());
            status.put("passed", sub.isPassed());
            status.put("submissionTime", sub.getSubmissionTime());
        } else {
            status.put("hasTaken", false);
        }

        return ResponseEntity.ok(status);
    }
    @DeleteMapping("/{examId}")
    @Operation(
            summary = "Delete exam",
            description = "Delete an exam (only draft exams without submissions can be deleted)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exam successfully deleted"),
            @ApiResponse(responseCode = "400", description = "Exam cannot be deleted (finalized or has submissions)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden - user doesn't own this exam"),
            @ApiResponse(responseCode = "404", description = "Exam not found")
    })
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> deleteExam(
            @Parameter(description = "ID of the exam to delete")
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // Verify user is a teacher
            boolean isTeacher = teacher.getRoles().stream()
                    .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

            if (!isTeacher) {
                throw new RuntimeException("Access denied: Only teachers can delete exams");
            }

            // Delete the exam
            examService.deleteExam(examId, teacher);

            // Prepare success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Exam deleted successfully");
            response.put("examId", examId);

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
    @GetMapping("/submissions/{submissionId}/answers")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> getSubmissionAnswers(@PathVariable Long submissionId, Authentication authentication) {
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            // بررسی دسترسی
            if (!submission.getStudent().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
            }

            return ResponseEntity.ok(submissionService.getSubmissionWithAnswers(submissionId));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching answers");
        }
    }
}