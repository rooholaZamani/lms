// src/main/java/com/example/demo/service/LessonAccessService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LessonAccessService {

    private final LessonRepository lessonRepository;
    private final ProgressRepository progressRepository;
    private final SubmissionRepository submissionRepository;
    private final ExerciseSubmissionRepository exerciseSubmissionRepository;

    public LessonAccessService(
            LessonRepository lessonRepository,
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            ExerciseSubmissionRepository exerciseSubmissionRepository) {
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
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

        // Get student's progress in this course
        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        if (progressOpt.isEmpty()) {
            // No progress yet - can only access first lesson
            return isFirstLesson(targetLesson);
        }

        Progress progress = progressOpt.get();

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

        // Check if all previous lessons are completed
        for (int i = 0; i < targetIndex; i++) {
            Lesson previousLesson = allLessons.get(i);
            
            // Check if previous lesson is completed
            if (!progress.getCompletedLessons().contains(previousLesson.getId())) {
                return false;
            }

            // Check if previous lesson's exam is passed (if exists)
            if (previousLesson.getExam() != null) {
                if (!isExamPassed(student, previousLesson.getExam())) {
                    return false;
                }
            }

            // Check if previous lesson's exercise is passed (if exists)
            if (previousLesson.getExercise() != null) {
                if (!isExercisePassed(student, previousLesson.getExercise())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isFirstLesson(Lesson lesson) {
        List<Lesson> courseLessons = lessonRepository.findByCourseOrderByOrderIndex(lesson.getCourse());
        return !courseLessons.isEmpty() && courseLessons.get(0).getId().equals(lesson.getId());
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

        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        if (progressOpt.isEmpty()) {
            if (!isFirstLesson(targetLesson)) {
                return "You must start with the first lesson";
            }
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

    @Transactional
    public Lesson createLesson(Lesson lesson, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Validate duration is provided
        if (lesson.getDuration() == null || lesson.getDuration() <= 0) {
            throw new RuntimeException("Lesson duration must be provided and greater than 0");
        }

        lesson.setCourse(course);

        // Set order index if not provided
        if (lesson.getOrderIndex() == null) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            lesson.setOrderIndex(lessons.size());
        }

        return lessonRepository.save(lesson);
    }
}