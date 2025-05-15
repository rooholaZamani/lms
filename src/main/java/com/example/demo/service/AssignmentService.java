package com.example.demo.service;

import com.example.demo.model.Assignment;
import com.example.demo.model.AssignmentSubmission;
import com.example.demo.model.FileMetadata;
import com.example.demo.model.Lesson;
import com.example.demo.model.User;
import com.example.demo.repository.AssignmentRepository;
import com.example.demo.repository.AssignmentSubmissionRepository;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LessonRepository lessonRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageService fileStorageService;

    public AssignmentService(
            AssignmentRepository assignmentRepository,
            AssignmentSubmissionRepository submissionRepository,
            LessonRepository lessonRepository,
            FileMetadataRepository fileMetadataRepository,
            FileStorageService fileStorageService) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.lessonRepository = lessonRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Create a new assignment for a lesson
     */
    @Transactional
    public Assignment createAssignment(
            Long lessonId,
            String title,
            String description,
            MultipartFile file,
            String dueDateStr,
            User teacher) {

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        Assignment assignment = new Assignment();
        assignment.setTitle(title);
        assignment.setDescription(description);
        assignment.setLesson(lesson);
        assignment.setTeacher(teacher);
        assignment.setCreatedAt(LocalDateTime.now());

        // Parse due date
        LocalDateTime dueDate = LocalDateTime.parse(dueDateStr);
        assignment.setDueDate(dueDate);

        // Handle file upload if provided
        if (file != null && !file.isEmpty()) {
            String subdirectory = fileStorageService.generatePath(
                    lesson.getCourse().getId(),
                    lessonId,
                    "assignments");

            FileMetadata metadata = fileStorageService.storeFile(file, subdirectory);
            assignment.setFile(metadata);
        }

        return assignmentRepository.save(assignment);
    }

    /**
     * Get all assignments for a lesson
     */
    public List<Assignment> getLessonAssignments(Long lessonId) {
        return assignmentRepository.findByLessonId(lessonId);
    }

    /**
     * Get assignment by ID
     */
    public Assignment getAssignmentById(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));
    }

    /**
     * Submit an assignment
     */
    @Transactional
    public AssignmentSubmission submitAssignment(
            Long assignmentId,
            User student,
            MultipartFile file,
            String comment) {

        Assignment assignment = getAssignmentById(assignmentId);

        // Check if student already has a submission
        List<AssignmentSubmission> existingSubmissions = submissionRepository.findByAssignment(assignment);

        AssignmentSubmission submission = existingSubmissions.stream()
                .filter(s -> s.getStudent().getId().equals(student.getId()))
                .findFirst()
                .orElse(new AssignmentSubmission());

        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setComment(comment);
        submission.setSubmittedAt(LocalDateTime.now());

        // Preserve existing grade if resubmitting
        if (submission.getId() == null) {
            submission.setGraded(false);
            submission.setScore(null);
            submission.setFeedback(null);
        }

        // Handle file upload
        if (file != null && !file.isEmpty()) {
            // If replacing an existing file, delete it first
            if (submission.getFile() != null) {
                fileStorageService.deleteFile(submission.getFile());
                fileMetadataRepository.delete(submission.getFile());
            }

            // Store the new file
            String subdirectory = fileStorageService.generatePath(
                    assignment.getLesson().getCourse().getId(),
                    assignment.getLesson().getId(),
                    "submissions/" + student.getId());

            FileMetadata metadata = fileStorageService.storeFile(file, subdirectory);
            submission.setFile(metadata);
        }

        return submissionRepository.save(submission);
    }

    /**
     * Get all submissions for an assignment
     */
    public List<AssignmentSubmission> getAssignmentSubmissions(Long assignmentId) {
        Assignment assignment = getAssignmentById(assignmentId);
        return submissionRepository.findByAssignment(assignment);
    }

    /**
     * Grade a submission
     */
    @Transactional
    public AssignmentSubmission gradeSubmission(
            Long submissionId,
            Integer score,
            String feedback,
            User teacher) {

        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // Verify the assignment belongs to the teacher's course
        Assignment assignment = submission.getAssignment();
        if (!assignment.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized to grade this submission");
        }

        submission.setScore(score);
        submission.setFeedback(feedback);
        submission.setGraded(true);
        submission.setGradedAt(LocalDateTime.now());

        return submissionRepository.save(submission);
    }

    /**
     * Get all submissions for a student
     */
    public List<AssignmentSubmission> getStudentSubmissions(User student) {
        return submissionRepository.findByStudent(student);
    }

    /**
     * Get file metadata by ID
     */
    public FileMetadata getFileMetadataById(Long fileId) {
        return fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
    }
}