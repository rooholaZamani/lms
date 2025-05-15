package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public AssignmentController(
            AssignmentService assignmentService,
            UserService userService,
            FileStorageService fileStorageService) {
        this.assignmentService = assignmentService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<Assignment> createAssignment(
            @PathVariable Long lessonId,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("dueDate") String dueDate,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Assignment assignment = assignmentService.createAssignment(lessonId, title, description, file, dueDate, teacher);
        return ResponseEntity.ok(assignment);
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<List<Assignment>> getLessonAssignments(@PathVariable Long lessonId) {
        List<Assignment> assignments = assignmentService.getLessonAssignments(lessonId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<Assignment> getAssignment(@PathVariable Long assignmentId) {
        Assignment assignment = assignmentService.getAssignmentById(assignmentId);
        return ResponseEntity.ok(assignment);
    }

    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<AssignmentSubmission> submitAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("comment") String comment,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        AssignmentSubmission submission = assignmentService.submitAssignment(assignmentId, student, file, comment);
        return ResponseEntity.ok(submission);
    }

    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<AssignmentSubmission>> getAssignmentSubmissions(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<AssignmentSubmission> submissions = assignmentService.getAssignmentSubmissions(assignmentId);
        return ResponseEntity.ok(submissions);
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<AssignmentSubmission> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestParam("score") Integer score,
            @RequestParam("feedback") String feedback,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        AssignmentSubmission gradedSubmission = assignmentService.gradeSubmission(submissionId, score, feedback, teacher);
        return ResponseEntity.ok(gradedSubmission);
    }

    @GetMapping("/submissions/student")
    public ResponseEntity<List<AssignmentSubmission>> getStudentSubmissions(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<AssignmentSubmission> submissions = assignmentService.getStudentSubmissions(student);
        return ResponseEntity.ok(submissions);
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
}