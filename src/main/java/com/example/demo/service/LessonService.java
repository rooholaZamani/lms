// src/main/java/com/example/demo/service/LessonService.java
package com.example.demo.service;

import com.example.demo.model.Course;
import com.example.demo.model.Lesson;
import com.example.demo.model.User;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    public List<Lesson> getTeacherLessons(User teacher) {
        // Get all courses taught by this teacher
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        List<Lesson> allLessons = new ArrayList<>();

        // Get all lessons from all courses
        for (Course course : teacherCourses) {
            List<Lesson> courseLessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            allLessons.addAll(courseLessons);
        }

        return allLessons;
    }

    public void deleteLesson(Long lessonId) {
        lessonRepository.deleteById(lessonId);
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