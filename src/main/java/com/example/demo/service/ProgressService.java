// src/main/java/com/example/demo/service/ProgressService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final ContentRepository contentRepository;
    private final SubmissionRepository submissionRepository;
    private final ExerciseSubmissionRepository exerciseSubmissionRepository;

    public ProgressService(
            ProgressRepository progressRepository,
            LessonRepository lessonRepository,
            ContentRepository contentRepository,
            SubmissionRepository submissionRepository,
            ExerciseSubmissionRepository exerciseSubmissionRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.contentRepository = contentRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
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
                    progress.setTotalLessons(course.getLessons().size());
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

        // Update completion metrics
        int totalLessons = course.getLessons().size();
        int completedLessons = progress.getCompletedLessons().size();

        progress.setTotalLessons(totalLessons);
        progress.setCompletedLessonCount(completedLessons);
        progress.setCompletionPercentage(
                totalLessons > 0 ? (double) completedLessons / totalLessons * 100 : 0);

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
        checkAndCompleteLessonIfReady(student, lesson);

        return updatedProgress;
    }

    private void checkAndCompleteLessonIfReady(User student, Lesson lesson) {
        Course course = lesson.getCourse();
        Progress progress = getOrCreateProgress(student, course);

        // Get all content IDs in this lesson
        List<Long> lessonContentIds = lesson.getContents().stream()
                .map(Content::getId)
                .collect(Collectors.toList());

        // Check if all content is completed
        boolean allContentCompleted = progress.getCompletedContent().containsAll(lessonContentIds);

        // Check if lesson has exam and if it's completed
        boolean examCompleted = true;
        if (lesson.getExam() != null) {
            // Check if student has passed the exam
            examCompleted = submissionRepository.findByStudentAndExam(student, lesson.getExam())
                    .map(Submission::isPassed)
                    .orElse(false);
        }

        // Check if lesson has exercise and if it's completed
        boolean exerciseCompleted = true;
        if (lesson.getExercise() != null) {
            // Check if student has passed the exercise
            exerciseCompleted = exerciseSubmissionRepository.findByStudent(student).stream()
                    .anyMatch(sub -> sub.getExercise().getId().equals(lesson.getExercise().getId()) && sub.isPassed());
        }

        // Auto-complete lesson if all requirements are met
        if (allContentCompleted && examCompleted && exerciseCompleted) {
            if (!progress.getCompletedLessons().contains(lesson.getId())) {
                markLessonComplete(student, lesson.getId());
            }
        }
    }
}