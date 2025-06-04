// src/main/java/com/example/demo/service/LessonCompletionService.java
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
    private final ExerciseSubmissionRepository exerciseSubmissionRepository;
    private final ContentRepository contentRepository;

    public LessonCompletionService(
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            ExerciseSubmissionRepository exerciseSubmissionRepository,
            ContentRepository contentRepository) {
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
        this.contentRepository = contentRepository;
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
        
        // 3. Check exercise is passed (if exists)
        return lesson.getExercise() == null || isExercisePassed(student, lesson.getExercise());
    }

    private boolean isExamPassed(User student, Exam exam) {
        Optional<Submission> submission = submissionRepository.findByStudentAndExam(student, exam);
        return submission.isPresent() && submission.get().isPassed();
    }

    private boolean isExercisePassed(User student, Exercise exercise) {
        List<ExerciseSubmission> submissions = exerciseSubmissionRepository.findByStudent(student);
        return submissions.stream()
                .anyMatch(sub -> sub.getExercise().getId().equals(exercise.getId()) && sub.isPassed());
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

        // Check if lesson has exercise and if it's completed
        boolean exerciseCompleted = true;
        if (lesson.getExercise() != null) {
            exerciseCompleted = exerciseSubmissionRepository.findByStudent(student).stream()
                    .anyMatch(sub -> sub.getExercise().getId().equals(lesson.getExercise().getId()) && sub.isPassed());
        }

        // Should auto-complete if all requirements are met
        return allContentCompleted && examCompleted && exerciseCompleted;
    }

    // Also add the missing method:
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


}