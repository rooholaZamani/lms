package com.example.demo.service;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.ProgressRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;

    public CourseService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
    }

    public Course createCourse(Course course, User teacher) {
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }

    public List<Course> getTeacherCourses(User teacher) {
        return courseRepository.findByTeacher(teacher);
    }

    public List<Course> getEnrolledCourses(User student) {
        return courseRepository.findByEnrolledStudentsContaining(student);
    }

    public Course enrollStudent(Long courseId, User student) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getEnrolledStudents().contains(student)) {
            course.getEnrolledStudents().add(student);
            courseRepository.save(course);

            // Initialize progress tracking
            Progress progress = new Progress();
            progress.setStudent(student);
            progress.setCourse(course);
            progress.setLastAccessed(LocalDateTime.now());
            progress.setTotalLessons(course.getLessons().size());
            progress.setCompletedLessonCount(0);
            progress.setCompletionPercentage(0.0);
            progressRepository.save(progress);
        }

        return course;
    }

    // Other methods as needed...
}