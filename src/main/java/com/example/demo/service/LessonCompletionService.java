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
     * چک کردن اینکه آیا درس واقعاً کامل شده یا نه
     */
    public boolean isLessonCompleted(User student, Lesson lesson) {
        Course course = lesson.getCourse();
        
        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        if (progressOpt.isEmpty()) {
            return false;
        }
        
        Progress progress = progressOpt.get();
        
        // 1. چک کردن اینکه درس در لیست کامل شده‌ها باشد
        boolean isMarkedComplete = progress.getCompletedLessons().contains(lesson.getId());
        
        // 2. اگر صرفاً مارک شده، چک کنیم که واقعاً شرایط کامل شدن را داشته باشد
        if (isMarkedComplete) {
            return validateLessonCompletion(student, lesson, progress);
        }
        
        // 3. اگر مارک نشده، چک کنیم آیا شرایط کامل شدن را دارد یا نه
        return checkAndAutoCompleteLessonIfReady(student, lesson, progress);
    }

    /**
     * تأیید اینکه درس واقعاً شرایط کامل شدن را دارد
     */
    private boolean validateLessonCompletion(User student, Lesson lesson, Progress progress) {
        // 1. چک کردن محتوا
        if (!areAllContentsCompleted(lesson, progress)) {
            return false;
        }
        
        // 2. چک کردن آزمون
        if (lesson.getExam() != null && !isExamPassed(student, lesson.getExam())) {
            return false;
        }
        
        // 3. چک کردن تمرین
        if (lesson.getExercise() != null && !isExercisePassed(student, lesson.getExercise())) {
            return false;
        }
        
        return true;
    }

    /**
     * چک کردن و خودکار کامل کردن درس اگر شرایط فراهم باشد
     */
    private boolean checkAndAutoCompleteLessonIfReady(User student, Lesson lesson, Progress progress) {
        // چک کردن شرایط
        if (!areAllContentsCompleted(lesson, progress)) {
            return false;
        }
        
        if (lesson.getExam() != null && !isExamPassed(student, lesson.getExam())) {
            return false;
        }
        
        if (lesson.getExercise() != null && !isExercisePassed(student, lesson.getExercise())) {
            return false;
        }
        
        // اگر همه شرایط فراهم است، خودکار کامل کن
        progress.getCompletedLessons().add(lesson.getId());
        progressRepository.save(progress);
        
        return true;
    }

    /**
     * چک کردن اینکه همه محتوای درس دیده شده
     */
    private boolean areAllContentsCompleted(Lesson lesson, Progress progress) {
        List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
        
        for (Content content : lessonContents) {
            if (!progress.getCompletedContent().contains(content.getId())) {
                return false;
            }
        }
        
        return true;
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
     * دریافت جزئیات تکمیل درس
     */
    public LessonCompletionStatus getLessonCompletionStatus(User student, Lesson lesson) {
        Course course = lesson.getCourse();
        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        
        if (progressOpt.isEmpty()) {
            return new LessonCompletionStatus(false, "No progress found", null);
        }
        
        Progress progress = progressOpt.get();
        List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
        
        // چک کردن محتوا
        int totalContents = lessonContents.size();
        int completedContents = 0;
        for (Content content : lessonContents) {
            if (progress.getCompletedContent().contains(content.getId())) {
                completedContents++;
            }
        }
        
        // چک کردن آزمون
        boolean examPassed = true;
        String examStatus = "No exam";
        if (lesson.getExam() != null) {
            examPassed = isExamPassed(student, lesson.getExam());
            examStatus = examPassed ? "Passed" : "Not passed";
        }
        
        // چک کردن تمرین
        boolean exercisePassed = true;
        String exerciseStatus = "No exercise";
        if (lesson.getExercise() != null) {
            exercisePassed = isExercisePassed(student, lesson.getExercise());
            exerciseStatus = exercisePassed ? "Passed" : "Not passed";
        }
        
        boolean isCompleted = (completedContents == totalContents) && examPassed && exercisePassed;
        
        LessonCompletionDetails details = new LessonCompletionDetails(
            completedContents, totalContents, examStatus, exerciseStatus
        );
        
        return new LessonCompletionStatus(isCompleted, 
            isCompleted ? "Lesson completed" : "Lesson not completed", details);
    }

    // Helper classes
    public static class LessonCompletionStatus {
        private final boolean completed;
        private final String message;
        private final LessonCompletionDetails details;

        public LessonCompletionStatus(boolean completed, String message, LessonCompletionDetails details) {
            this.completed = completed;
            this.message = message;
            this.details = details;
        }

        // Getters
        public boolean isCompleted() { return completed; }
        public String getMessage() { return message; }
        public LessonCompletionDetails getDetails() { return details; }
    }

    public static class LessonCompletionDetails {
        private final int completedContents;
        private final int totalContents;
        private final String examStatus;
        private final String exerciseStatus;

        public LessonCompletionDetails(int completedContents, int totalContents, 
                                     String examStatus, String exerciseStatus) {
            this.completedContents = completedContents;
            this.totalContents = totalContents;
            this.examStatus = examStatus;
            this.exerciseStatus = exerciseStatus;
        }

        // Getters
        public int getCompletedContents() { return completedContents; }
        public int getTotalContents() { return totalContents; }
        public String getExamStatus() { return examStatus; }
        public String getExerciseStatus() { return exerciseStatus; }
    }
}