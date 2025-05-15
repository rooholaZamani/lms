package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.ProgressService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
//@CrossOrigin(origins = "*")
public class CourseController {

    private final CourseService courseService;
    private final UserService userService;
    private final ProgressService progressService;

    public CourseController(
            CourseService courseService,
            UserService userService,
            ProgressService progressService) {
        this.courseService = courseService;
        this.userService = userService;
        this.progressService = progressService;
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(
            @RequestBody Course course,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Course savedCourse = courseService.createCourse(course, teacher);
        return ResponseEntity.ok(savedCourse);
    }

    @GetMapping("/teaching")
    public ResponseEntity<List<Course>> getTeacherCourses(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getTeacherCourses(teacher);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/enrolled")
    public ResponseEntity<List<Course>> getEnrolledCourses(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getEnrolledCourses(student);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/available")
    public ResponseEntity<List<Course>> getAvailableCourses(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<Course> allCourses = courseService.getAllCourses();
        List<Course> enrolledCourses = courseService.getEnrolledCourses(user);

        // Filter out courses the student is already enrolled in
        allCourses.removeAll(enrolledCourses);

        return ResponseEntity.ok(allCourses);
    }

    @PostMapping("/{courseId}/enroll")
    public ResponseEntity<Course> enrollInCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.enrollStudent(courseId, student);
        return ResponseEntity.ok(course);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<Map<String, Object>> getCourseDetails(
            @PathVariable Long courseId,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);

        Map<String, Object> response = new HashMap<>();
        response.put("course", course);

        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));
        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        if (isStudent) {
            Progress progress = progressService.getOrCreateProgress(user, course);
            response.put("progress", progress);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<Course>> getAllCourses() {
        List<Course> courses = courseService.getAllCourses();
        return ResponseEntity.ok(courses);
    }
}