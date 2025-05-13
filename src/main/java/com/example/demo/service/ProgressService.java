// src/main/java/com/example/demo/service/ProgressService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.ContentRepository;
import com.example.demo.repository.LessonRepository;
import com.example.demo.repository.ProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final ContentRepository contentRepository;

    public ProgressService(
            ProgressRepository progressRepository,
            LessonRepository lessonRepository,
            ContentRepository contentRepository) {
        this.progressRepository = progressRepository;
        this.lessonRepository = lessonRepository;
        this.contentRepository = contentRepository;
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
}