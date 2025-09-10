package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final ContentRepository contentRepository;
    private final SubmissionRepository submissionRepository;
    private final LessonCompletionService lessonCompletionService;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ExamRepository examRepository;

    public ProgressService(
            ProgressRepository progressRepository,
            LessonRepository lessonRepository,
            ContentRepository contentRepository,
            SubmissionRepository submissionRepository,
            LessonCompletionService lessonCompletionService,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            AssignmentRepository assignmentRepository,
            ExamRepository examRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.contentRepository = contentRepository;
        this.submissionRepository = submissionRepository;
        this.lessonCompletionService = lessonCompletionService;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.examRepository = examRepository;
    }

    public List<Progress> getProgressByStudent(User student) {
        return progressRepository.findByStudent(student);
    }

    public Progress getOrCreateProgress(User student, Course course) {
        return progressRepository.findByStudentAndCourse(student, course)
                .orElseGet(() -> {
                    Progress progress = new Progress();
                    progress.setStudent(student);
                    progress.setCourse(course);
                    progress.setLastAccessed(LocalDateTime.now());
                    // Use repository to get accurate lesson count instead of lazy-loaded collection
                    int totalLessons = lessonRepository.findByCourseOrderByOrderIndex(course).size();
                    progress.setTotalLessons(totalLessons);
                    progress.setCompletedLessonCount(0);
                    progress.setCompletionPercentage(0.0);
                    return progressRepository.save(progress);
                });
    }

    @Transactional
    public Progress markLessonComplete(User student, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        Course course = lesson.getCourse();
        Progress progress = getOrCreateProgress(student, course);

        progress.getCompletedLessons().add(lessonId);
        progress.setLastAccessed(LocalDateTime.now());

        // Update completion metrics using activity-based calculation
        // Use repository to get accurate lesson count instead of lazy-loaded collection
        int totalLessons = lessonRepository.findByCourseOrderByOrderIndex(course).size();
        int completedLessons = progress.getCompletedLessons().size();

        progress.setTotalLessons(totalLessons);
        progress.setCompletedLessonCount(completedLessons);
        
        // Use activity-based calculation for accurate completion percentage
        double calculatedProgress = calculateProgressFromActivities(student, course);
        progress.setCompletionPercentage(calculatedProgress);

        return progressRepository.save(progress);
    }

    @Transactional
    public Progress markContentViewed(User student, Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found"));

        Lesson lesson = content.getLesson();
        Course course = lesson.getCourse();
        Progress progress = getOrCreateProgress(student, course);

        progress.getViewedContent().add(contentId);
        progress.setLastAccessed(LocalDateTime.now());

        return progressRepository.save(progress);
    }

    @Transactional
    public Progress markContentComplete(User student, Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found"));

        Lesson lesson = content.getLesson();
        Course course = lesson.getCourse();
        Progress progress = getOrCreateProgress(student, course);

        // Mark content as completed and viewed
        progress.getCompletedContent().add(contentId);
        progress.getViewedContent().add(contentId);
        progress.setLastAccessed(LocalDateTime.now());

        Progress updatedProgress = progressRepository.save(progress);

        // Check if lesson should be auto-completed
        lessonCompletionService.checkAndAutoCompleteLesson(student, lesson);

        return updatedProgress;
    }

    /**
     * Calculate course completion progress based on granular activities 
     * (content viewing/completion, exam submissions, assignment submissions)
     * This method provides fine-grained progress tracking
     */
    private double calculateProgressFromActivities(User student, Course course) {
        // Get all lessons in the course
        List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
        if (lessons.isEmpty()) return 0.0;

        // Get student's progress record
        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        
        // Initialize counters for granular activities
        int totalActivities = 0;
        int completedActivities = 0;

        for (Lesson lesson : lessons) {
            // 1. COUNT AND CHECK CONTENT ACTIVITIES
            List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
            totalActivities += lessonContents.size();
            
            if (progressOpt.isPresent()) {
                Progress progress = progressOpt.get();
                // Count completed content (either viewed or explicitly completed)
                for (Content content : lessonContents) {
                    if (progress.getCompletedContent().contains(content.getId()) || 
                        progress.getViewedContent().contains(content.getId())) {
                        completedActivities++;
                    }
                }
            }

            // 2. COUNT AND CHECK EXAM ACTIVITIES
            if (examRepository.findByLessonId(lesson.getId()).isPresent()) {
                totalActivities++;
                
                Exam exam = examRepository.findByLessonId(lesson.getId()).get();
                Optional<Submission> submission = submissionRepository.findByStudentAndExam(student, exam);
                if (submission.isPresent()) {
                    // Count any exam submission (regardless of pass/fail for progress tracking)
                    completedActivities++;
                }
            }

            // 3. COUNT AND CHECK ASSIGNMENT ACTIVITIES
            List<Assignment> lessonAssignments = assignmentRepository.findByLesson(lesson);
            totalActivities += lessonAssignments.size();
            
            for (Assignment assignment : lessonAssignments) {
                Optional<AssignmentSubmission> submission = 
                    assignmentSubmissionRepository.findByStudentAndAssignment(student, assignment);
                if (submission.isPresent()) {
                    completedActivities++;
                }
            }
        }

        // Calculate granular progress percentage
        if (totalActivities == 0) {
            return 0.0; // No activities in course
        }

        return Math.min(100.0, (double) completedActivities / totalActivities * 100);
    }

    /**
     * Fix existing Progress records that have incorrect totalLessons counts
     * due to previous lazy-loading issues
     */
    @Transactional
    public void fixIncorrectTotalLessons() {
        List<Progress> allProgress = progressRepository.findAll();
        
        for (Progress progress : allProgress) {
            if (progress.getCourse() != null) {
                int actualTotalLessons = lessonRepository.findByCourseOrderByOrderIndex(progress.getCourse()).size();
                
                // Update if the stored value is incorrect
                if (progress.getTotalLessons() == null || !progress.getTotalLessons().equals(actualTotalLessons)) {
                    progress.setTotalLessons(actualTotalLessons);
                    progressRepository.save(progress);
                }
            }
        }
    }
}