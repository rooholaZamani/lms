package com.example.demo.controller;

import com.example.demo.dto.AssignmentDTO;
import com.example.demo.dto.AssignmentSubmissionDTO;
import com.example.demo.model.*;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.FileStorageService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final DTOMapperService dtoMapperService;

    public AssignmentController(
            AssignmentService assignmentService,
            UserService userService,
            FileStorageService fileStorageService,
            DTOMapperService dtoMapperService) {
        this.assignmentService = assignmentService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/lesson/{lessonId}")
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
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTO(submission));
    }

    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<List<AssignmentSubmissionDTO>> getAssignmentSubmissions(
            @PathVariable Long assignmentId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<AssignmentSubmission> submissions = assignmentService.getAssignmentSubmissions(assignmentId);
        return ResponseEntity.ok(dtoMapperService.mapToAssignmentSubmissionDTOList(submissions));
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
}