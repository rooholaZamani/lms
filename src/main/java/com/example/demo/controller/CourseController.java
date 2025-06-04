package com.example.demo.controller;

import com.example.demo.dto.CourseDTO;
import com.example.demo.dto.CourseDetailsDTO;
import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.ProgressService;
import com.example.demo.service.UserService;
import com.example.demo.service.DTOMapperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Course management operations")
public class CourseController {

    private final UserService userService;
    private final ProgressService progressService;
    private final DTOMapperService dtoMapperService;
    private final CourseService courseService;

    public CourseController(
            CourseService courseService,
            UserService userService,
            ProgressService progressService,
            DTOMapperService dtoMapperService) {

        this.userService = userService;
        this.progressService = progressService;
        this.dtoMapperService = dtoMapperService;
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<CourseDTO> createCourse(
            @RequestBody Course course,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Course savedCourse = courseService.createCourse(course, teacher);
        return ResponseEntity.ok(dtoMapperService.mapToCourseDTO(savedCourse));
    }

    @GetMapping("/teaching")
    public ResponseEntity<List<CourseDTO>> getTeacherCourses(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getTeacherCourses(teacher);
        List<CourseDTO> courseDTOs = dtoMapperService.mapToCourseDTOListSummary(courses);
        return ResponseEntity.ok(courseDTOs);
    }

    // 🔥 FIXED: Now returns CourseDTO instead of raw Course entity
    @GetMapping("/enrolled")
    @Operation(summary = "Get enrolled courses", description = "Get list of courses the student is enrolled in")
    public ResponseEntity<List<CourseDTO>> getEnrolledCourses(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getEnrolledCourses(student);
        List<CourseDTO> courseDTOs = dtoMapperService.mapToCourseDTOListSummary(courses);
        return ResponseEntity.ok(courseDTOs);
    }

    // 🔥 FIXED: Now returns CourseDTO instead of raw Course entity
    @GetMapping("/available")
    @Operation(summary = "Get available courses", description = "Get list of courses available for enrollment")
    public ResponseEntity<List<CourseDTO>> getAvailableCourses(Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        List<Course> allCourses = courseService.getAllCourses();
        List<Course> enrolledCourses = courseService.getEnrolledCourses(user);

        // Filter out courses the student is already enrolled in
        allCourses.removeAll(enrolledCourses);

        // Convert to DTOs
        List<CourseDTO> courseDTOs = dtoMapperService.mapToCourseDTOListSummary(allCourses);
        return ResponseEntity.ok(courseDTOs);
    }

    @PostMapping("/{courseId}/enroll")
    public ResponseEntity<CourseDTO> enrollInCourse(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.enrollStudent(courseId, student);
        return ResponseEntity.ok(dtoMapperService.mapToCourseDTO(course));
    }

    // 🔥 FIXED: Now returns a cleaner response using CourseDetailsDTO
    @GetMapping("/{courseId}")
    @Operation(summary = "Get course details", description = "Get detailed information about a specific course")
    public ResponseEntity<CourseDetailsDTO> getCourseDetails(
            @PathVariable Long courseId,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);

        CourseDTO courseDTO = dtoMapperService.mapToCourseDTO(course, true);

        // Create detailed response
        CourseDetailsDTO response = new CourseDetailsDTO();
        response.setCourse(courseDTO);

        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));
        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        response.setIsTeacher(isTeacher);
        response.setIsStudent(isStudent);

        if (isStudent) {
            Progress progress = progressService.getOrCreateProgress(user, course);
            response.setProgress(dtoMapperService.mapToProgressDTO(progress));
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<CourseDTO>> getAllCourses() {
        List<Course> courses = courseService.getAllCourses();
        List<CourseDTO> courseDTOs = dtoMapperService.mapToCourseDTOListSummary(courses);
        return ResponseEntity.ok(courseDTOs);
    }
}