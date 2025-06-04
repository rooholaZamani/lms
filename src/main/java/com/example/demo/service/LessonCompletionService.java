// src/main/java/com/example/demo/service/LessonCompletionService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

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
}