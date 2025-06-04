package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class LessonAccessService {

    private final LessonRepository lessonRepository;
    private final ProgressRepository progressRepository;
    private final LessonAccessService lessonAccessService;
    private final LessonCompletionService lessonCompletionService; // Add this

    public LessonAccessService(
            LessonRepository lessonRepository,
            ProgressRepository progressRepository, LessonAccessService lessonAccessService,
            LessonCompletionService lessonCompletionService) { // Add this parameter
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.lessonAccessService = lessonAccessService;
        this.lessonCompletionService = lessonCompletionService; // Add this
    }

    /**
     * Check if a student can access a specific lesson
     */
    public boolean canAccessLesson(User student, Long lessonId) {
        Lesson targetLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        Course course = targetLesson.getCourse();

        // Check if student is enrolled in the course
        if (!course.getEnrolledStudents().contains(student)) {
            return false;
        }

        // Get all lessons in the course ordered by orderIndex
        List<Lesson> allLessons = lessonRepository.findByCourseOrderByOrderIndex(course);

        // Find the target lesson's position
        int targetIndex = -1;
        for (int i = 0; i < allLessons.size(); i++) {
            if (allLessons.get(i).getId().equals(lessonId)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            return false; // Lesson not found in course
        }

        // First lesson is always accessible
        if (targetIndex == 0) {
            return true;
        }

        // Check if all previous lessons are completed using LessonCompletionService
        for (int i = 0; i < targetIndex; i++) {
            Lesson previousLesson = allLessons.get(i);
            if (!lessonCompletionService.isLessonCompleted(student, previousLesson)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get access info for a lesson
     */
    public LessonAccessInfo getLessonAccessInfo(User student, Long lessonId) {
        boolean canAccess = canAccessLesson(student, lessonId);
        String reason = "";

        if (!canAccess) {
            reason = getAccessDeniedReason(student, lessonId);
        }

        return new LessonAccessInfo(canAccess, reason);
    }

    private String getAccessDeniedReason(User student, Long lessonId) {
        Lesson targetLesson = lessonRepository.findById(lessonId)
                .orElse(null);

        if (targetLesson == null) {
            return "Lesson not found";
        }

        Course course = targetLesson.getCourse();

        if (!course.getEnrolledStudents().contains(student)) {
            return "You are not enrolled in this course";
        }

        return "You must complete previous lessons and their exams/exercises";
    }

    public static class LessonAccessInfo {
        private final boolean canAccess;
        private final String reason;

        public LessonAccessInfo(boolean canAccess, String reason) {
            this.canAccess = canAccess;
            this.reason = reason;
        }

        public boolean isCanAccess() { return canAccess; }
        public String getReason() { return reason; }
    }
}