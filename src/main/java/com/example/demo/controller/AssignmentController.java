package com.example.demo.controller;

import com.example.demo.dto.AssignmentDTO;
import com.example.demo.dto.AssignmentSubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.service.ActivityTrackingService;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.LessonCompletionService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final ActivityTrackingService activityTrackingService;
    private final AssignmentService assignmentService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;
    private final LessonCompletionService lessonCompletionService;

    public AssignmentController(
            ActivityTrackingService activityTrackingService,
            AssignmentService assignmentService,
            UserService userService,
            FileStorageService fileStorageService,
            DTOMapperService dtoMapperService,
            LessonCompletionService lessonCompletionService) {
        this.activityTrackingService = activityTrackingService;
        this.assignmentService = assignmentService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
        this.lessonCompletionService = lessonCompletionService;
    }

    @PostMapping(value = "/lesson/{lessonId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a new assignment",
            description = "Create a new assignment for a specific lesson with optional file attachment"
    )
    public ResponseEntity<AssignmentDTO> createAssignment(
            @Parameter(description = "ID of the lesson") @PathVariable Long lessonId,
            @Parameter(description = "Title of the assignment") @RequestParam("title") String title,
            @Parameter(description = "Description of the assignment") @RequestParam("description") String description,
            @Parameter(description = "File attachment (optional)") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Due date in ISO-8601 format (yyyy-MM-ddTHH:mm:ss)") @RequestParam("dueDate") String dueDate,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Assignment assignment = assignmentService.createAssignment(lessonId, title, description, file, dueDate, teacher);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentDTO(assignment));
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<List<AssignmentDTO>> getLessonAssignments(@PathVariable Long lessonId) {
        List<Assignment> assignments = assignmentService.getLessonAssignments(lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentDTOList(assignments));
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentDTO> getAssignment(@PathVariable Long assignmentId) {
        Assignment assignment = assignmentService.getAssignmentById(assignmentId);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentDTO(assignment));
    }

    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<AssignmentSubmissionDTO> submitAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("comment") String comment,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        AssignmentSubmission submission = assignmentService.submitAssignment(assignmentId, student, file, comment);

        // Activity tracking for assignment submission
        Assignment assignment = submission.getAssignment();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("assignmentTitle", assignment.getTitle());
        metadata.put("lessonId", assignment.getLesson().getId().toString());
        metadata.put("lessonTitle", assignment.getLesson().getTitle());
        metadata.put("courseId", assignment.getLesson().getCourse().getId().toString());
        metadata.put("courseTitle", assignment.getLesson().getCourse().getTitle());
        if (submission.getComment() != null && !submission.getComment().trim().isEmpty()) {
            metadata.put("hasComment", "true");
        }
        if (file != null && !file.isEmpty()) {
            metadata.put("hasFile", "true");
            metadata.put("fileName", file.getOriginalFilename());
        }

        // Estimate time spent (assignments typically take 30-60 minutes)
        Long timeSpent = 1800L; // 30 minutes default

        activityTrackingService.logActivity(student, "ASSIGNMENT_SUBMISSION", submission.getId(), timeSpent, metadata);
        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }

        // Check for lesson auto-completion after assignment submission
        lessonCompletionService.checkAndAutoCompleteLesson(student, submission.getAssignment().getLesson());

        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTO(submission));
    }

    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<Map<String, Object>> getAssignmentSubmissions(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());

        Assignment assignment = assignmentService.getAssignmentById(assignmentId);

        List<AssignmentSubmission> submissions = assignmentService.getAssignmentSubmissions(assignmentId);

        int totalStudents = assignment.getLesson().getCourse().getEnrolledStudents().size();

        int submittedStudents = submissions.size();

        double averageScore = submissions.stream()
                .filter(s -> s.isGraded() && s.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        long lateSubmissions = submissions.stream()
                .filter(s -> s.getSubmittedAt().isAfter(assignment.getDueDate()))
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("submissions", dtoMapperService.mapToAssignmentSubmissionDTOList(submissions));
        response.put("totalStudents", totalStudents);
        response.put("submittedStudents", submittedStudents);
        response.put("averageScore", averageScore);
        response.put("lateSubmissions", lateSubmissions);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<AssignmentSubmissionDTO> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> gradeData,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());

        Integer score = (Integer) gradeData.get("score");
        String feedback = (String) gradeData.get("feedback");

        AssignmentSubmission gradedSubmission = assignmentService.gradeSubmission(submissionId, score, feedback, teacher);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTO(gradedSubmission));
    }

    @GetMapping("/submissions/student")
    public ResponseEntity<List<AssignmentSubmissionDTO>> getStudentSubmissions(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<AssignmentSubmission> submissions = assignmentService.getStudentSubmissions(student);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTOList(submissions));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<Resource> getFile(
            @PathVariable Long fileId,
            HttpServletRequest request) {

        FileMetadata metadata = assignmentService.getFileMetadataById(fileId);
        Resource resource = fileStorageService.loadFileAsResource(metadata.getFilePath());

        String contentType = metadata.getContentType();
        String disposition = "attachment; filename=\"" + metadata.getOriginalFilename() + "\"";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }
    @GetMapping("/submissions/student/{studentId}")
    @Operation(
            summary = "Get student submissions by student ID",
            description = "Get all assignment submissions for a specific student (teacher access only)"
    )
    public ResponseEntity<List<AssignmentSubmissionDTO>> getStudentSubmissionsById(
            @PathVariable Long studentId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can view student submissions");
        }

        // Get the student
        User student = userService.findByUsername(studentId.toString()); // اگر studentId همان username باشد
        // یا اگر نیاز به repository دارید:
        // User student = userRepository.findById(studentId)
        //     .orElseThrow(() -> new RuntimeException("Student not found"));

        // Verify student is in teacher's courses
        List<AssignmentSubmission> submissions = assignmentService.getStudentSubmissions(student);

        // Filter submissions to only include assignments from teacher's courses
        List<AssignmentSubmission> filteredSubmissions = submissions.stream()
                .filter(submission -> submission.getAssignment().getTeacher().getId().equals(teacher.getId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTOList(filteredSubmissions));
    }
}