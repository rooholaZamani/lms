// src/main/java/com/example/demo/service/LessonService.java
package com.example.demo.service;

import com.example.demo.model.Course;
import com.example.demo.model.Lesson;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.LessonRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;

    public LessonService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
    }

    public Lesson createLesson(Lesson lesson, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        lesson.setCourse(course);

        // Set order index if not provided
        if (lesson.getOrderIndex() == null) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            lesson.setOrderIndex(lessons.size());
        }

        return lessonRepository.save(lesson);
    }

    public List<Lesson> getCourseLessons(Long courseId) {
        return lessonRepository.findByCourseIdOrderByOrderIndex(courseId);
    }

    public Lesson getLessonById(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
    }

    public Lesson updateLesson(Long lessonId, Lesson lessonDetails) {
        Lesson lesson = getLessonById(lessonId);

        lesson.setTitle(lessonDetails.getTitle());
        lesson.setDescription(lessonDetails.getDescription());
        if (lessonDetails.getOrderIndex() != null) {
            lesson.setOrderIndex(lessonDetails.getOrderIndex());
        }

        return lessonRepository.save(lesson);
    }

    public void deleteLesson(Long lessonId) {
        lessonRepository.deleteById(lessonId);
    }
}