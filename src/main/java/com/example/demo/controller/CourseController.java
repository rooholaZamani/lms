package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Course management API endpoints")
public class CourseController {

    private final CourseService courseService;
    private final UserService userService;

    public CourseController(
            CourseService courseService,
            UserService userService) {
        this.courseService = courseService;
        this.userService = userService;
    }

    @Operation(summary = "Create a new course", description = "Create a new course (requires teacher role)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Course created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Course.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires teacher role")
    })
    @PostMapping
    public ResponseEntity<Course> createCourse(
            @RequestBody Course course,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Course savedCourse = courseService.createCourse(course, teacher);
        return ResponseEntity.ok(savedCourse);
    }

    @Operation(summary = "Get courses taught by authenticated teacher", description = "Returns a list of courses taught by the authenticated teacher")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retrieved teacher's courses",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires teacher role")
    })
    @GetMapping("/teaching")
    public ResponseEntity<List<Course>> getTeacherCourses(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getTeacherCourses(teacher);
        return ResponseEntity.ok(courses);
    }

    @Operation(summary = "Get courses enrolled by authenticated student", description = "Returns a list of courses the authenticated student is enrolled in")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retrieved student's enrolled courses",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires student role")
    })
    @GetMapping("/enrolled")
    public ResponseEntity<List<Course>> getEnrolledCourses(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getEnrolledCourses(student);
        return ResponseEntity.ok(courses);
    }

    @Operation(summary = "Enroll student in course", description = "Enrolls the authenticated student in a specified course")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully enrolled in course",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Course.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Course not found")
    })
    @PostMapping("/{courseId}/enroll")
    public ResponseEntity<Course> enrollInCourse(
            @Parameter(description = "ID of the course to enroll in") @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.enrollStudent(courseId, student);
        return ResponseEntity.ok(course);
    }
}