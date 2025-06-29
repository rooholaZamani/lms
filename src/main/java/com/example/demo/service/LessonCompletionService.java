package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LessonCompletionService {

    private final ProgressRepository progressRepository;
    private final SubmissionRepository submissionRepository;
    private final ContentRepository contentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;

    public LessonCompletionService(
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            ContentRepository contentRepository,
            AssignmentRepository assignmentRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository) {
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.contentRepository = contentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
    }

    /**
     * Get detailed completion status for a lesson
     */
    public LessonCompletionStatus getLessonCompletionStatus(User student, Lesson lesson) {
        Course course = lesson.getCourse();

        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        Progress progress = progressOpt.orElse(null);

        // Get all contents in this lesson
        List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);

        // Check content completion
        int totalContents = lessonContents.size();
        int completedContents = 0;

        if (progress != null) {
            for (Content content : lessonContents) {
                if (progress.getCompletedContent().contains(content.getId())) {
                    completedContents++;
                }
            }
        }

        // Check exam status
        boolean hasExam = lesson.getExam() != null;
        boolean examPassed = false;
        if (hasExam) {
            examPassed = isExamPassed(student, lesson.getExam());
        }

        // Check assignment status (replacing exercise logic)
        List<Assignment> lessonAssignments = assignmentRepository.findByLessonId(lesson.getId());
        boolean hasAssignment = !lessonAssignments.isEmpty();
        boolean assignmentCompleted = false;
        if (hasAssignment) {
            assignmentCompleted = areAllAssignmentsCompleted(student, lessonAssignments);
        }

        // Overall completion status
        boolean isCompleted = progress != null && progress.getCompletedLessons().contains(lesson.getId());

        // Calculate completion percentage
        int totalRequirements = totalContents + (hasExam ? 1 : 0) + (hasAssignment ? 1 : 0);
        int completedRequirements = completedContents + (examPassed ? 1 : 0) + (assignmentCompleted ? 1 : 0);

        double completionPercentage = totalRequirements > 0 ?
                (double) completedRequirements / totalRequirements * 100 : 100.0;

        return new LessonCompletionStatus(
                isCompleted,
                completionPercentage,
                totalContents,
                completedContents,
                hasExam,
                examPassed,
                hasAssignment, // Using assignment instead of exercise
                assignmentCompleted, // Using assignment completion instead of exercise
                totalRequirements,
                completedRequirements
        );
    }

    /**
     * Check if a lesson is truly completed
     */
    public boolean isLessonCompleted(User student, Lesson lesson) {
        Course course = lesson.getCourse();

        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        if (progressOpt.isEmpty()) {
            return false;
        }

        Progress progress = progressOpt.get();

        // Check if lesson is marked as completed in progress
        if (!progress.getCompletedLessons().contains(lesson.getId())) {
            return false;
        }

        // Validate that completion requirements are actually met
        return validateLessonCompletion(student, lesson, progress);
    }

    private boolean validateLessonCompletion(User student, Lesson lesson, Progress progress) {
        // 1. Check all contents are completed
        List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
        for (Content content : lessonContents) {
            if (!progress.getCompletedContent().contains(content.getId())) {
                return false;
            }
        }

        // 2. Check exam is passed (if exists)
        if (lesson.getExam() != null && !isExamPassed(student, lesson.getExam())) {
            return false;
        }

        // 3. Check all assignments are completed (if any exist)
        List<Assignment> lessonAssignments = assignmentRepository.findByLessonId(lesson.getId());
        if (!lessonAssignments.isEmpty() && !areAllAssignmentsCompleted(student, lessonAssignments)) {
            return false;
        }

        return true;
    }

    private boolean isExamPassed(User student, Exam exam) {
        Optional<Submission> submission = submissionRepository.findByStudentAndExam(student, exam);
        return submission.isPresent() && submission.get().isPassed();
    }

    /**
     * Check if all assignments in a lesson are completed by the student
     */
    private boolean areAllAssignmentsCompleted(User student, List<Assignment> assignments) {
        for (Assignment assignment : assignments) {
            List<AssignmentSubmission> submissions = assignmentSubmissionRepository.findByAssignment(assignment);

            // Check if student has submitted this assignment
            boolean hasSubmitted = submissions.stream()
                    .anyMatch(submission -> submission.getStudent().getId().equals(student.getId()));

            if (!hasSubmitted) {
                return false; // At least one assignment is not submitted
            }
        }
        return true; // All assignments are submitted
    }

    private boolean shouldAutoComplete(User student, Lesson lesson) {
        Course course = lesson.getCourse();

        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        if (progressOpt.isEmpty()) {
            return false;
        }

        Progress progress = progressOpt.get();

        // Don't auto-complete if already completed
        if (progress.getCompletedLessons().contains(lesson.getId())) {
            return false;
        }

        // Get all content IDs in this lesson
        List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
        List<Long> lessonContentIds = lessonContents.stream()
                .map(Content::getId)
                .collect(Collectors.toList());

        // Check if all content is completed
        boolean allContentCompleted = progress.getCompletedContent().containsAll(lessonContentIds);

        // Check if lesson has exam and if it's completed
        boolean examCompleted = true;
        if (lesson.getExam() != null) {
            examCompleted = submissionRepository.findByStudentAndExam(student, lesson.getExam())
                    .map(Submission::isPassed)
                    .orElse(false);
        }

        // Check if all assignments are completed
        boolean assignmentsCompleted = true;
        List<Assignment> lessonAssignments = assignmentRepository.findByLessonId(lesson.getId());
        if (!lessonAssignments.isEmpty()) {
            assignmentsCompleted = areAllAssignmentsCompleted(student, lessonAssignments);
        }

        // Should auto-complete if all requirements are met
        return allContentCompleted && examCompleted && assignmentsCompleted;
    }

    @Transactional
    public void checkAndAutoCompleteLesson(User student, Lesson lesson) {
        if (shouldAutoComplete(student, lesson)) {
            // Call ProgressService to mark lesson complete
            Progress progress = progressRepository.findByStudentAndCourse(student, lesson.getCourse())
                    .orElseThrow(() -> new RuntimeException("Progress not found"));

            progress.getCompletedLessons().add(lesson.getId());
            progress.setLastAccessed(LocalDateTime.now());

            // Update completion metrics
            int totalLessons = lesson.getCourse().getLessons().size();
            int completedLessons = progress.getCompletedLessons().size();

            progress.setTotalLessons(totalLessons);
            progress.setCompletedLessonCount(completedLessons);
            progress.setCompletionPercentage(
                    totalLessons > 0 ? (double) completedLessons / totalLessons * 100 : 0);

            progressRepository.save(progress);
        }
    }

    /**
     * Inner class to represent detailed lesson completion status
     */
    public static class LessonCompletionStatus {
        private final boolean isCompleted;
        private final double completionPercentage;
        private final int totalContents;
        private final int completedContents;
        private final boolean hasExam;
        private final boolean examPassed;
        private final boolean hasAssignment; // Changed from hasExercise
        private final boolean assignmentCompleted; // Changed from exercisePassed
        private final int totalRequirements;
        private final int completedRequirements;

        public LessonCompletionStatus(boolean isCompleted, double completionPercentage,
                                      int totalContents, int completedContents,
                                      boolean hasExam, boolean examPassed,
                                      boolean hasAssignment, boolean assignmentCompleted,
                                      int totalRequirements, int completedRequirements) {
            this.isCompleted = isCompleted;
            this.completionPercentage = completionPercentage;
            this.totalContents = totalContents;
            this.completedContents = completedContents;
            this.hasExam = hasExam;
            this.examPassed = examPassed;
            this.hasAssignment = hasAssignment;
            this.assignmentCompleted = assignmentCompleted;
            this.totalRequirements = totalRequirements;
            this.completedRequirements = completedRequirements;
        }

        // Getters
        public boolean isCompleted() { return isCompleted; }
        public double getCompletionPercentage() { return completionPercentage; }
        public int getTotalContents() { return totalContents; }
        public int getCompletedContents() { return completedContents; }
        public boolean isHasExam() { return hasExam; }
        public boolean isExamPassed() { return examPassed; }
        public boolean isHasAssignment() { return hasAssignment; } // Changed from isHasExercise
        public boolean isAssignmentCompleted() { return assignmentCompleted; } // Changed from isExercisePassed
        public int getTotalRequirements() { return totalRequirements; }
        public int getCompletedRequirements() { return completedRequirements; }
    }
}