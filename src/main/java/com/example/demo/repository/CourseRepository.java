package com.example.demo.repository;

import com.example.demo.model.Course;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByTeacher(User teacher);
    List<Course> findByEnrolledStudentsContaining(User student);
    List<Course> findByActiveTrue();
    List<Course> findByActiveFalse();
    List<Course> findByActive(Boolean active);
    List<Course> findByTeacherAndActiveTrue(User teacher);
    List<Course> findByTeacherAndActiveFalse(User teacher);
    List<Course> findByTeacherAndActive(User teacher, Boolean active);
}