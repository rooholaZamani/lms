package com.example.demo.controller;


import com.example.demo.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import java.io.ByteArrayOutputStream;
import com.example.demo.dto.ExamDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.dto.SubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.service.ExamService;
import com.example.demo.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.example.demo.dto.ExamWithDetailsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
@RestController
@RequestMapping("/api/exams")
@Tag(name = "Exams", description = "Exam management operations")
public class ExamController {

    @Autowired
    private QuestionRepository questionRepository;

    private final ExamService examService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;
    private final ActivityTrackingService activityTrackingService;
    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;
    private final LessonRepository lessonRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final LessonCompletionService lessonCompletionService;
    private final ProgressService progressService;
    public ExamController(
            ExamService examService,
            UserService userService,
            DTOMapperService dtoMapperService, ActivityTrackingService activityTrackingService, SubmissionRepository submissionRepository, SubmissionService submissionService, LessonRepository lessonRepository, ExamRepository examRepository, UserRepository userRepository, LessonCompletionService lessonCompletionService, ProgressService progressService) {
        this.examService = examService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
        this.activityTrackingService = activityTrackingService;
        this.submissionRepository = submissionRepository;
        this.submissionService = submissionService;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.lessonCompletionService = lessonCompletionService;

        this.progressService = progressService;
    }


    @PostMapping("/lesson/{lessonId}")
    @Operation(summary = "Create a new exam", description = "Create a new exam for a specific lesson")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> createExam(
            @PathVariable Long lessonId,
            @RequestBody Exam exam,
            @RequestParam(defaultValue = "false") boolean forceReplace,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());
            Exam savedExam = examService.createExam(exam, lessonId, forceReplace);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("exam", dtoMapperService.mapToExamDTO(savedExam));
            response.put("id", savedExam.getId()); // اضافه کردن id برای backward compatibility
            response.put("message", "آزمون با موفقیت ایجاد شد");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("EXAM_EXISTS_WITH_SUBMISSIONS:")) {
                String[] parts = e.getMessage().split(":");
                int submissionCount = Integer.parseInt(parts[1]);

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("errorType", "EXAM_EXISTS_WITH_SUBMISSIONS");
                errorResponse.put("submissionCount", submissionCount);
                errorResponse.put("message", "این درس قبلاً آزمون دارد که " + submissionCount + " دانش‌آموز در آن شرکت کرده‌اند.");

                return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
            }

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
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
        }
        Exam exam = examService.getExamById(examId);
        ExamDTO examDTO = dtoMapperService.mapToExamDTO(exam, user);


        Map<String, String> metadata = new HashMap<>();
        metadata.put("examTitle", exam.getTitle());
        metadata.put("lessonId", exam.getLesson().getId().toString());
        metadata.put("lessonTitle", exam.getLesson().getTitle());
        metadata.put("courseId", exam.getLesson().getCourse().getId().toString());
        metadata.put("courseTitle", exam.getLesson().getCourse().getTitle());

        activityTrackingService.logActivity(user, "EXAM_START", examId, 0L, metadata);

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
    @Transactional
    public ResponseEntity<SubmissionDTO> submitExam(
            @PathVariable Long examId,
            @RequestBody Map<String, Object> submissionData,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());

        if (examService.hasStudentTakenExam(examId, student)) {
            throw new RuntimeException("You have already submitted this exam");
        }

        // Process answers for different question types
        Object answersObj = submissionData.get("answers");
        String answersJson = "{}";

        if (answersObj instanceof Map) {
            try {
                // اضافه کردن logging برای debug
                System.out.println("Received answers: " + answersObj);

                // Convert to JSON string using ObjectMapper
                answersJson = objectMapper.writeValueAsString(answersObj);
                System.out.println("Converted to JSON: " + answersJson);

            } catch (Exception e) {
                System.err.println("Error converting answers to JSON: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Invalid answers format: " + e.getMessage());
            }
        }

        Long timeSpent = submissionData.get("timeSpent") != null ?
                ((Number) submissionData.get("timeSpent")).longValue() : 0L;

        // Validate time spent is reasonable
        Exam exam = examService.findById(examId);
        if (timeSpent > 0) {
            // Time spent should not be more than 2x the exam time limit (allow for some buffer)
            if (exam.getTimeLimit() != null) {
                long maxAllowedTime = exam.getTimeLimit() * 60 * 2; // Convert minutes to seconds and double it
                if (timeSpent > maxAllowedTime) {
                    // Log but don't reject - could be network issues or browser issues
                    System.out.println("Warning: Student spent " + timeSpent + " seconds on exam, which exceeds 2x time limit of " + exam.getTimeLimit() + " minutes");
                }
            }
            
            // Time spent should be at least 10 seconds (basic sanity check)
            if (timeSpent < 10) {
                System.out.println("Warning: Student spent only " + timeSpent + " seconds on exam, which seems too fast");
            }
        }

        // Submit exam with JSON answers
        Submission submission = examService.submitExam(examId, student, answersJson);

        // Update submission with time spent
        submission = examService.updateSubmissionTimeSpent(submission, timeSpent);

        // Activity tracking
        Map<String, String> metadata = new HashMap<>();
        metadata.put("examTitle", exam.getTitle());
        metadata.put("lessonId", exam.getLesson().getId().toString());
        metadata.put("lessonTitle", exam.getLesson().getTitle());
        metadata.put("courseId", exam.getLesson().getCourse().getId().toString());
        metadata.put("courseTitle", exam.getLesson().getCourse().getTitle());
        metadata.put("questionsCount", String.valueOf(exam.getQuestions().size()));

        activityTrackingService.logActivity(student, "EXAM_SUBMISSION", examId, timeSpent, metadata);
        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }
        lessonCompletionService.checkAndAutoCompleteLesson(student, exam.getLesson());
        return ResponseEntity.ok(dtoMapperService.mapToSubmissionDTO(submission));
    }


    private String convertObjectToJson(Object obj) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                first = false;

                json.append("\"").append(entry.getKey().toString()).append("\":");
                json.append("\"").append(entry.getValue().toString()).append("\"");
            }

            json.append("}");
            return json.toString();
        }
        return "\"" + obj.toString() + "\"";
    }

    private String convertListToJson(List<?> list) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (Object item : list) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(item.toString()).append("\"");
        }

        json.append("]");
        return json.toString();
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
    @PutMapping("/{examId}")
    @Operation(summary = "Update exam", description = "Update exam details (only draft exams can be updated)")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<ExamDTO> updateExam(
            @PathVariable Long examId,
            @RequestBody Exam examData,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can update exams");
        }

        Exam updatedExam = examService.updateExam(examId, examData, teacher);
        return ResponseEntity.ok(dtoMapperService.mapToExamDTO(updatedExam));
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
    @GetMapping("/{examId}/student-answers")
    @Operation(summary = "Get student answers for exam", description = "Retrieve detailed answers for student submission")
    public ResponseEntity<Map<String, Object>> getStudentAnswers(
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User student = userService.findByUsername(authentication.getName());

            // پیدا کردن submission دانش‌آموز
            Submission submission = submissionRepository.findByExamIdAndStudent(examId, student)
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            Exam exam = submission.getExam();
            List<Question> questions = exam.getQuestions();

            // Parse کردن answers از JSON
            Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());
            System.out.println("Parsed student answers: " + studentAnswers);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> answersDetails = new HashMap<>();

            int totalEarnedPoints = 0;

            for (Question question : questions) {
                String questionId = String.valueOf(question.getId());

                // پیدا کردن پاسخ دانش‌آموز برای این سوال
                Object studentAnswer = studentAnswers.get(questionId);

                // اگر پاسخی پیدا نشد، تلاش کن با ایندکس سوال نیز بیابی
                if (studentAnswer == null) {
                    // تلاش با ایندکس سوال
                    int questionIndex = questions.indexOf(question);
                    studentAnswer = studentAnswers.get(String.valueOf(questionIndex));
                    System.out.println("Question " + questionId + " not found, trying index " + questionIndex + ": " + studentAnswer);
                }



                if (studentAnswer == null) {
                    // تنظیم پاسخ خالی برای نمایش بهتر
                    switch (question.getQuestionType()) {
                        case MATCHING:
                        case CATEGORIZATION:
                            studentAnswer = new HashMap<>();
                            break;
                        case FILL_IN_THE_BLANKS:
                            studentAnswer = new ArrayList<>();
                            break;
                        case ESSAY:
                        case SHORT_ANSWER:
                            studentAnswer = "";
                            break;
                        default:
                            studentAnswer = null;
                    }
                }

                System.out.println("Processing question " + questionId + " with answer: " + studentAnswer);

                // ارزیابی پاسخ
                Map<String, Object> evaluation = evaluateStudentAnswer(question, studentAnswer);

                // اضافه کردن اطلاعات اضافی
                evaluation.put("studentAnswer", studentAnswer);
                evaluation.put("questionType", question.getQuestionType().toString());
                evaluation.put("questionText", question.getText());
                evaluation.put("questionOptions", getQuestionOptions(question));
                answersDetails.put(questionId, evaluation);
                totalEarnedPoints += (Integer) evaluation.get("earnedPoints");
            }

            response.put("answers", answersDetails);
            response.put("score", totalEarnedPoints);
            response.put("totalPossibleScore", submission.getExam().getTotalPossibleScore());
            response.put("passed", totalEarnedPoints >= submission.getExam().getPassingScore());
            response.put("submissionTime", submission.getSubmissionTime());
            response.put("timeSpent", submission.getTimeSpent());
            response.put("success", true);

            // اگر نمره محاسبه شده با نمره ذخیره شده متفاوت است، به‌روزرسانی کن
            if (!Objects.equals(submission.getScore(), totalEarnedPoints)) {
                System.out.println("Score mismatch detected. Stored: " + submission.getScore() +
                        ", Calculated: " + totalEarnedPoints + ". Updating submission...");

                submission.setScore(totalEarnedPoints);
                submission.setPassed(totalEarnedPoints >= exam.getPassingScore());
                submission = submissionRepository.save(submission);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error getting student answers: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "خطا در دریافت پاسخ‌ها: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    // متد کمکی برای parse کردن JSON answers
    private Map<String, Object> parseAnswersJson(String answersJson) {
        if (answersJson == null || answersJson.trim().isEmpty() || answersJson.trim().equals("{}")) {
            System.out.println("Empty or null answers JSON");
            return new HashMap<>();
        }

        try {
            System.out.println("Parsing answers JSON: " + answersJson);
            Map<String, Object> parsed = objectMapper.readValue(answersJson, new TypeReference<Map<String, Object>>() {
            });
            System.out.println("Successfully parsed: " + parsed);
            return parsed;
        } catch (Exception e) {
            System.err.println("Error parsing answers JSON: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
    public Map<String, Object> evaluateStudentAnswer(Question question, Object studentAnswer) {
        Map<String, Object> result = new HashMap<>();

        System.out.println("Evaluating question " + question.getId() + " of type " + question.getQuestionType()
                + " with student answer: " + studentAnswer);

        boolean isCorrect = false;
        int earnedPoints = 0;

        try {
            isCorrect = examService.evaluateAnswer(question, studentAnswer);
            earnedPoints = isCorrect ? question.getPoints() : 0;
        } catch (Exception e) {
            System.err.println("Error evaluating answer: " + e.getMessage());
            e.printStackTrace();
        }

        result.put("isCorrect", isCorrect);
        result.put("earnedPoints", earnedPoints);
        result.put("totalPoints", question.getPoints());

        // Add correct answer info based on question type
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                Optional<Answer> correctAnswer = question.getAnswers().stream()
                        .filter(Answer::getCorrect)
                        .findFirst();
                result.put("correctAnswer", correctAnswer.map(Answer::getId).orElse(null));
                break;

            case CATEGORIZATION:
                Map<String, String> correctCategories = new HashMap<>();
                for (Answer answer : question.getAnswers()) {
                    correctCategories.put(answer.getText(), answer.getCategory());
                }
                result.put("correctAnswer", correctCategories);
                break;

            case MATCHING:
                Map<String, String> correctMatches = new HashMap<>();
                for (MatchingPair pair : question.getMatchingPairs()) {
                    correctMatches.put(pair.getLeftItem(), pair.getRightItem());
                }
                result.put("correctAnswer", correctMatches);
                break;

            case FILL_IN_THE_BLANKS:
                List<String> correctAnswers = question.getBlankAnswers().stream()
                        .map(BlankAnswer::getCorrectAnswer)
                        .collect(Collectors.toList());
                result.put("correctAnswer", correctAnswers);
                break;

            case SHORT_ANSWER:
            case ESSAY:
                // برای این نوع سوالات، پاسخ صحیح خاصی نداریم
                result.put("correctAnswer", "نیاز به بررسی دستی");
                break;
        }

        return result;
    }
    @GetMapping("/lesson/{lessonId}/status")
    @Operation(summary = "Check lesson exam status", description = "Check if lesson has exam and submission count")
    public ResponseEntity<Map<String, Object>> checkLessonExamStatus(@PathVariable Long lessonId) {
        try {
            Lesson lesson = lessonRepository.findById(lessonId)
                    .orElseThrow(() -> new RuntimeException("Lesson not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("hasExam", lesson.getExam() != null);

            if (lesson.getExam() != null) {
                List<Submission> submissions = submissionRepository.findByExam(lesson.getExam());
                response.put("submissionCount", submissions.size());
                response.put("examTitle", lesson.getExam().getTitle());
                response.put("examStatus", lesson.getExam().getStatus());
            } else {
                response.put("submissionCount", 0);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    private Map<String, Object> getQuestionOptions(Question question) {
        Map<String, Object> options = new HashMap<>();

        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                List<Map<String, Object>> answers = question.getAnswers().stream()
                        .map(answer -> {
                            Map<String, Object> answerMap = new HashMap<>();
                            answerMap.put("id", answer.getId());
                            answerMap.put("text", answer.getText());
                            answerMap.put("isCorrect", answer.getCorrect());
                            return answerMap;
                        })
                        .collect(Collectors.toList());
                options.put("answers", answers);
                break;

            case MATCHING:
                List<Map<String, Object>> pairs = question.getMatchingPairs().stream()
                        .map(pair -> {
                            Map<String, Object> pairMap = new HashMap<>();
                            pairMap.put("leftItem", pair.getLeftItem());
                            pairMap.put("rightItem", pair.getRightItem());
                            return pairMap;
                        })
                        .collect(Collectors.toList());
                options.put("matchingPairs", pairs);
                break;

            case CATEGORIZATION:
                List<Map<String, Object>> categories = question.getAnswers().stream()
                        .map(answer -> {
                            Map<String, Object> categoryMap = new HashMap<>();
                            categoryMap.put("text", answer.getText());
                            categoryMap.put("category", answer.getCategory());
                            return categoryMap;
                        })
                        .collect(Collectors.toList());
                options.put("categories", categories);
                break;

            case FILL_IN_THE_BLANKS:
                List<String> correctAnswers = question.getBlankAnswers().stream()
                        .map(BlankAnswer::getCorrectAnswer)
                        .collect(Collectors.toList());
                options.put("blankAnswers", correctAnswers);
                break;
        }

        return options;
    }
    @PostMapping("/submissions/{submissionId}/manual-grade")
    @Operation(summary = "Manual grading for essay and short answer questions")
    @SecurityRequirement(name = "basicAuth")
    @Transactional
    public ResponseEntity<?> manualGradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> gradingData,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // پیدا کردن submission
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            // بررسی دسترسی معلم
            if (!submission.getExam().getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied"));
            }

            // دریافت نمرات دستی از request
            @SuppressWarnings("unchecked")
            Map<String, Object> manualGrades = (Map<String, Object>) gradingData.get("manualGrades");
            String feedback = (String) gradingData.get("feedback");

            // Parse کردن answers فعلی
            Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());

            // اعمال نمرات دستی
            List<Question> questions = submission.getExam().getQuestions();
            int totalScore = 0;

            for (Question question : questions) {
                String questionId = String.valueOf(question.getId());

                if (manualGrades.containsKey(questionId)) {
                    // نمره دستی برای این سوال
                    int manualScore = ((Number) manualGrades.get(questionId)).intValue();
                    totalScore += manualScore;
                } else {
                    // نمره خودکار برای سوالات غیرتشریحی
                    Object studentAnswer = studentAnswers.get(questionId);
                    boolean isCorrect = examService.evaluateAnswer(question, studentAnswer);
                    totalScore += isCorrect ? question.getPoints() : 0;
                }
            }

            // به‌روزرسانی submission
            submission.setScore(totalScore);
            submission.setPassed(totalScore >= submission.getExam().getPassingScore());
            submission.setGradedManually(true);
            submission.setGradedBy(teacher);
            submission.setGradedAt(LocalDateTime.now());
            submission.setFeedback(feedback);

            // ذخیره نمرات دستی در JSON جداگانه
            ObjectMapper objectMapper = new ObjectMapper();
            String manualGradesJson = objectMapper.writeValueAsString(manualGrades);
            submission.setManualGradesJson(manualGradesJson);

            submissionRepository.save(submission);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "نمره‌گذاری با موفقیت انجام شد");
            response.put("totalScore", totalScore);
            response.put("passed", submission.isPassed());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error in manual grading: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطا در نمره‌گذاری: " + e.getMessage()));
        }
    }

    @GetMapping("/{examId}/submissions-for-grading")
    @Operation(summary = "Get submissions that need manual grading")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> getSubmissionsForGrading(
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // پیدا کردن آزمون
            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found"));

            // بررسی دسترسی معلم
            if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied"));
            }

            // پیدا کردن submissions
            List<Submission> submissions = submissionRepository.findByExam(exam);

            // بررسی اینکه آیا آزمون سوالات تشریحی/پاسخ کوتاه دارد
            boolean hasManualQuestions = exam.getQuestions().stream()
                    .anyMatch(q -> q.getQuestionType() == QuestionType.ESSAY ||
                            q.getQuestionType() == QuestionType.SHORT_ANSWER);

            List<Map<String, Object>> submissionData = new ArrayList<>();

            for (Submission submission : submissions) {
                Map<String, Object> submissionInfo = new HashMap<>();
                submissionInfo.put("id", submission.getId());
                submissionInfo.put("studentName", submission.getStudent().getFirstName() + " " +
                        (submission.getStudent().getLastName() != null ? submission.getStudent().getLastName() : ""));
                submissionInfo.put("studentUsername", submission.getStudent().getUsername());
                submissionInfo.put("submissionTime", submission.getSubmissionTime());
                submissionInfo.put("score", submission.getScore());
                submissionInfo.put("passed", submission.isPassed());
                submissionInfo.put("gradedManually", submission.getGradedManually() != null ? submission.getGradedManually() : false);
                submissionInfo.put("gradedAt", submission.getGradedAt());
                submissionInfo.put("feedback", submission.getFeedback());

                if (submission.getGradedBy() != null) {
                    submissionInfo.put("gradedBy", submission.getGradedBy().getFirstName() + " " +
                            (submission.getGradedBy().getLastName() != null ? submission.getGradedBy().getLastName() : ""));
                }

                // محاسبه تعداد سوالات که نیاز به نمره‌دهی دستی دارند
                long manualQuestionsCount = exam.getQuestions().stream()
                        .filter(q -> q.getQuestionType() == QuestionType.ESSAY ||
                                q.getQuestionType() == QuestionType.SHORT_ANSWER)
                        .count();

                submissionInfo.put("manualQuestionsCount", manualQuestionsCount);
                submissionInfo.put("needsManualGrading", hasManualQuestions &&
                        (submission.getGradedManually() == null || !submission.getGradedManually()));

                submissionData.add(submissionInfo);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("examTitle", exam.getTitle());
            response.put("hasManualQuestions", hasManualQuestions);
            response.put("submissions", submissionData);
            response.put("totalSubmissions", submissions.size());

            long needsGradingCount = submissionData.stream()
                    .filter(s -> (Boolean) s.get("needsManualGrading"))
                    .count();
            response.put("needsGradingCount", needsGradingCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error getting submissions for grading: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطا در دریافت اطلاعات: " + e.getMessage()));
        }
    }

    @GetMapping("/submissions/{submissionId}/grading-detail")
    @Operation(summary = "Get detailed submission for manual grading")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> getSubmissionGradingDetail(
            @PathVariable Long submissionId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // پیدا کردن submission
            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new RuntimeException("Submission not found"));

            // بررسی دسترسی معلم
            if (!submission.getExam().getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied"));
            }

            Exam exam = submission.getExam();
            List<Question> questions = exam.getQuestions();

            // Parse کردن answers از JSON
            Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());
            Map<String, Object> manualGrades = new HashMap<>();

            // Parse کردن نمرات دستی قبلی (اگر وجود دارد)
            if (submission.getManualGradesJson() != null && !submission.getManualGradesJson().trim().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    manualGrades = objectMapper.readValue(submission.getManualGradesJson(),
                            new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    System.err.println("Error parsing manual grades JSON: " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> questionsData = new HashMap<>();

            for (Question question : questions) {
                String questionId = String.valueOf(question.getId());
                Object studentAnswer = studentAnswers.get(questionId);

                Map<String, Object> questionData = new HashMap<>();
                questionData.put("id", question.getId());
                questionData.put("text", question.getText());
                questionData.put("questionType", question.getQuestionType().toString());
                questionData.put("points", question.getPoints());
                questionData.put("studentAnswer", studentAnswer != null ? studentAnswer : "");

                // اگر سوال تشریحی یا پاسخ کوتاه باشد
                if (question.getQuestionType() == QuestionType.ESSAY ||
                        question.getQuestionType() == QuestionType.SHORT_ANSWER) {

                    questionData.put("needsManualGrading", true);
                    questionData.put("manualGrade", manualGrades.getOrDefault(questionId, 0));
                } else {
                    questionData.put("needsManualGrading", false);
                    // محاسبه نمره خودکار
                    boolean isCorrect = examService.evaluateAnswer(question, studentAnswer);
                    questionData.put("autoGrade", isCorrect ? question.getPoints() : 0);
                    questionData.put("isCorrect", isCorrect);
                }

                questionsData.put(questionId, questionData);
            }

            response.put("success", true);
            response.put("submissionId", submission.getId());
            response.put("examTitle", exam.getTitle());
            response.put("studentName", submission.getStudent().getFirstName() + " " +
                    (submission.getStudent().getLastName() != null ? submission.getStudent().getLastName() : ""));
            response.put("submissionTime", submission.getSubmissionTime());
            response.put("currentScore", submission.getScore());
            response.put("currentFeedback", submission.getFeedback());
            response.put("gradedManually", submission.getGradedManually() != null ? submission.getGradedManually() : false);
            response.put("questions", questionsData);
            response.put("totalPossibleScore", exam.getTotalPossibleScore());
            response.put("passingScore", exam.getPassingScore());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error getting submission grading detail: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطا در دریافت جزئیات: " + e.getMessage()));
        }
    }

    @GetMapping("/{examId}/submissions")
    @Operation(summary = "Get all submissions for an exam")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> getExamSubmissions(
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // پیدا کردن آزمون
            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found"));

            // بررسی دسترسی معلم
            if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied"));
            }

            // دریافت submissions
            List<Submission> submissions = submissionRepository.findByExam(exam);

            List<Map<String, Object>> submissionData = new ArrayList<>();

            for (Submission submission : submissions) {
                Map<String, Object> submissionInfo = new HashMap<>();
                submissionInfo.put("id", submission.getId());
                submissionInfo.put("score", submission.getScore());
                submissionInfo.put("passed", submission.isPassed());
                submissionInfo.put("submissionTime", submission.getSubmissionTime());
                submissionInfo.put("timeSpent", submission.getTimeSpent());
                submissionInfo.put("gradedManually", submission.getGradedManually() != null ? submission.getGradedManually() : false);
                submissionInfo.put("gradedAt", submission.getGradedAt());
                submissionInfo.put("feedback", submission.getFeedback());

                // اطلاعات دانش‌آموز
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("id", submission.getStudent().getId());
                studentInfo.put("username", submission.getStudent().getUsername());
                studentInfo.put("firstName", submission.getStudent().getFirstName());
                studentInfo.put("lastName", submission.getStudent().getLastName());
                submissionInfo.put("student", studentInfo);

                submissionData.add(submissionInfo);
            }

            return ResponseEntity.ok(submissionData);

        } catch (Exception e) {
            System.err.println("Error getting exam submissions: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطا در دریافت اطلاعات: " + e.getMessage()));
        }
    }

    @GetMapping("/{examId}/export-results")
    @Operation(summary = "Export exam results to Excel")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<byte[]> exportExamResults(
            @PathVariable Long examId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // پیدا کردن آزمون
            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found"));

            // بررسی دسترسی معلم
            if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                throw new RuntimeException("Access denied");
            }

            // دریافت submissions
            List<Submission> submissions = submissionRepository.findByExam(exam);

            // ایجاد Excel file
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("نتایج آزمون");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "ردیف", "نام کاربری", "نام", "نام خانوادگی",
                    "نمره", "حداکثر نمره", "درصد", "وضعیت",
                    "زمان ارسال", "مدت زمان", "نمره‌گذاری دستی", "بازخورد"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (int i = 0; i < submissions.size(); i++) {
                Submission submission = submissions.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(submission.getStudent().getUsername());
                row.createCell(2).setCellValue(submission.getStudent().getFirstName());
                row.createCell(3).setCellValue(submission.getStudent().getLastName() != null ?
                        submission.getStudent().getLastName() : "");
                row.createCell(4).setCellValue(submission.getScore() != null ? submission.getScore() : 0);
                row.createCell(5).setCellValue(exam.getTotalPossibleScore());

                // محاسبه درصد
                double percentage = exam.getTotalPossibleScore() > 0 ?
                        ((double)(submission.getScore() != null ? submission.getScore() : 0) / exam.getTotalPossibleScore()) * 100 : 0;
                row.createCell(6).setCellValue(Math.round(percentage));

                row.createCell(7).setCellValue(submission.isPassed() ? "قبول" : "مردود");

                // زمان ارسال
                if (submission.getSubmissionTime() != null) {
                    Cell timeCell = row.createCell(8);
                    timeCell.setCellValue(submission.getSubmissionTime().toString());
                } else {
                    row.createCell(8).setCellValue("");
                }

                // مدت زمان
                if (submission.getTimeSpent() != null) {
                    long  timeSpent = submission.getTimeSpent();
                    long  hours = timeSpent / 3600;
                    long  minutes = (timeSpent % 3600) / 60;
                    long  seconds = timeSpent % 60;
                    String timeString = String.format("%d:%02d:%02d", hours, minutes, seconds);
                    row.createCell(9).setCellValue(timeString);
                } else {
                    row.createCell(9).setCellValue("");
                }

                row.createCell(10).setCellValue(submission.getGradedManually() != null && submission.getGradedManually() ? "بله" : "خیر");
                row.createCell(11).setCellValue(submission.getFeedback() != null ? submission.getFeedback() : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Convert to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] excelBytes = outputStream.toByteArray();
            outputStream.close();

            // Response headers
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            responseHeaders.setContentDispositionFormData("attachment",
                    "exam-results-" + examId + ".xlsx");
            responseHeaders.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            System.err.println("Error exporting exam results: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("خطا در ایجاد فایل Excel: " + e.getMessage());
        }
    }

    @GetMapping("/manual-grading-overview")
    @Operation(summary = "Get overview of exams needing manual grading")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<?> getManualGradingOverview(Authentication authentication) {
        try {
            User teacher = userService.findByUsername(authentication.getName());

            // دریافت تمام آزمون‌های معلم که دارای سوالات تشریحی هستند
            List<Exam> teacherExams = examRepository.findByTeacher(teacher);

            List<Map<String, Object>> examList = new ArrayList<>();

            for (Exam exam : teacherExams) {
                // بررسی اینکه آزمون دارای سوالات تشریحی است یا نه
                boolean hasManualQuestions = exam.getQuestions().stream()
                        .anyMatch(q -> q.getQuestionType() == QuestionType.ESSAY ||
                                q.getQuestionType() == QuestionType.SHORT_ANSWER);

                if (!hasManualQuestions) {
                    continue; // اگر سوال تشریحی ندارد، رد شو
                }

                // شمارش سوالات تشریحی
                long manualQuestionsCount = exam.getQuestions().stream()
                        .filter(q -> q.getQuestionType() == QuestionType.ESSAY ||
                                q.getQuestionType() == QuestionType.SHORT_ANSWER)
                        .count();

                // دریافت submissions این آزمون
                List<Submission> submissions = submissionRepository.findByExam(exam);

                // شمارش submissions که نیاز به نمره‌گذاری دستی دارند
                long pendingSubmissions = submissions.stream()
                        .filter(s -> s.getGradedManually() == null || !s.getGradedManually())
                        .count();

                // یافتن آخرین submission
                Optional<Submission> lastSubmission = submissions.stream()
                        .max((s1, s2) -> s1.getSubmissionTime().compareTo(s2.getSubmissionTime()));

                Map<String, Object> examInfo = new HashMap<>();
                examInfo.put("id", exam.getId());
                examInfo.put("title", exam.getTitle());
                examInfo.put("lessonTitle", exam.getLesson().getTitle());
                examInfo.put("courseTitle", exam.getLesson().getCourse().getTitle());
                examInfo.put("totalQuestions", exam.getQuestions().size());
                examInfo.put("manualQuestionsCount", manualQuestionsCount);
                examInfo.put("totalPossibleScore", exam.getTotalPossibleScore());
                examInfo.put("pendingSubmissions", pendingSubmissions);
                examInfo.put("totalSubmissions", submissions.size());
                examInfo.put("lastSubmission", lastSubmission.map(Submission::getSubmissionTime).orElse(null));

                examList.add(examInfo);
            }

            // مرتب‌سازی بر اساس تعداد submissions در انتظار (نزولی)
            examList.sort((e1, e2) -> Long.compare(
                    (Long) e2.get("pendingSubmissions"),
                    (Long) e1.get("pendingSubmissions")
            ));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("exams", examList);
            response.put("totalExams", examList.size());

            long totalPending = examList.stream()
                    .mapToLong(e -> (Long) e.get("pendingSubmissions"))
                    .sum();
            response.put("totalPendingSubmissions", totalPending);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error getting manual grading overview: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطا در دریافت اطلاعات: " + e.getMessage()));
        }
    }
    @GetMapping("/{examId}/student-answers/{studentId}")
    @Operation(summary = "Get student answers for teacher", description = "Teacher can view specific student's detailed answers")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> getStudentAnswersForTeacher(
            @PathVariable Long examId,
            @PathVariable Long studentId,
            Authentication authentication) {

        try {
            User teacher = userService.findByUsername(authentication.getName());

            // بررسی که کاربر معلم باشد
            boolean isTeacher = teacher.getRoles().stream()
                    .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

            if (!isTeacher) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied: Only teachers allowed"));
            }

            // بررسی که آزمون متعلق به معلم باشد
            Exam exam = examRepository.findById(examId)
                    .orElseThrow(() -> new RuntimeException("Exam not found"));

            if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Access denied: This exam doesn't belong to you"));
            }

            // پیدا کردن دانش‌آموز
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // پیدا کردن submission دانش‌آموز
            Submission submission = submissionRepository.findByExamIdAndStudent(examId, student)
                    .orElseThrow(() -> new RuntimeException("Student submission not found"));

            List<Question> questions = exam.getQuestions();

            // Parse کردن answers از JSON
            Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());
            System.out.println("Parsed student answers: " + studentAnswers);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> answersDetails = new HashMap<>();

            int totalEarnedPoints = 0;

            for (Question question : questions) {
                String questionId = String.valueOf(question.getId());

                // پیدا کردن پاسخ دانش‌آموز برای این سوال
                Object studentAnswer = studentAnswers.get(questionId);

                // اگر پاسخی پیدا نشد، تلاش کن با ایندکس سوال نیز بیابی
                if (studentAnswer == null) {
                    // تلاش با ایندکس سوال
                    int questionIndex = questions.indexOf(question);
                    studentAnswer = studentAnswers.get(String.valueOf(questionIndex));
                    System.out.println("Question " + questionId + " not found, trying index " + questionIndex + ": " + studentAnswer);
                }



                if (studentAnswer == null) {
                    // تنظیم پاسخ خالی برای نمایش بهتر
                    switch (question.getQuestionType()) {
                        case MATCHING:
                        case CATEGORIZATION:
                            studentAnswer = new HashMap<>();
                            break;
                        case FILL_IN_THE_BLANKS:
                            studentAnswer = new ArrayList<>();
                            break;
                        case ESSAY:
                        case SHORT_ANSWER:
                            studentAnswer = "";
                            break;
                        default:
                            studentAnswer = null;
                    }
                }

                System.out.println("Processing question " + questionId + " with answer: " + studentAnswer);

                // ارزیابی پاسخ
                Map<String, Object> evaluation = evaluateStudentAnswer(question, studentAnswer);

                // اضافه کردن اطلاعات اضافی
                evaluation.put("studentAnswer", studentAnswer);
                evaluation.put("questionType", question.getQuestionType().toString());
                evaluation.put("questionText", question.getText());
                evaluation.put("questionOptions", getQuestionOptions(question));
                answersDetails.put(questionId, evaluation);
                totalEarnedPoints += (Integer) evaluation.get("earnedPoints");
            }

            response.put("answers", answersDetails);
            response.put("score", totalEarnedPoints);
            response.put("totalPossibleScore", submission.getExam().getTotalPossibleScore());
            response.put("passed", totalEarnedPoints >= submission.getExam().getPassingScore());
            response.put("submissionTime", submission.getSubmissionTime());
            response.put("timeSpent", submission.getTimeSpent());
            response.put("success", true);
            response.put("studentName", submission.getStudent().getFirstName() + " " +
                    (submission.getStudent().getLastName() != null ? submission.getStudent().getLastName() : ""));


            // اگر نمره محاسبه شده با نمره ذخیره شده متفاوت است، به‌روزرسانی کن
            if (!Objects.equals(submission.getScore(), totalEarnedPoints)) {
                System.out.println("Score mismatch detected. Stored: " + submission.getScore() +
                        ", Calculated: " + totalEarnedPoints + ". Updating submission...");

                submission.setScore(totalEarnedPoints);
                submission.setPassed(totalEarnedPoints >= exam.getPassingScore());
                submission = submissionRepository.save(submission);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error getting student answers: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "خطا در دریافت پاسخ‌ها: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}