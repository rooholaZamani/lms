package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.AnalyticsService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final CourseRepository courseRepository;

    public AnalyticsController(AnalyticsService analyticsService,
                               UserService userService,
                               CourseRepository courseRepository) {
        this.analyticsService = analyticsService;
        this.userService = userService;
        this.courseRepository = courseRepository;
    }

    // Existing endpoints...

    @GetMapping("/student/{studentId}/course/{courseId}/detailed")
    public ResponseEntity<Map<String, Object>> getStudentDetailedAnalytics(
            @PathVariable Long studentId,
            @PathVariable Long courseId,
            Authentication authentication) {

        Map<String, Object> analysis = analyticsService.getStudentDetailedAnalysis(studentId, courseId);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/student/{studentId}/activity-timeline")
    public ResponseEntity<List<Map<String, Object>>> getStudentActivityTimeline(
            @PathVariable Long studentId,
            Authentication authentication) {

        List<Map<String, Object>> timeline = analyticsService.getStudentActivityTimeline(studentId);
        return ResponseEntity.ok(timeline);
    }

    @GetMapping("/student/{studentId}/exam-performance")
    public ResponseEntity<List<Map<String, Object>>> getStudentExamPerformance(
            @PathVariable Long studentId,
            Authentication authentication) {

        List<Map<String, Object>> performance = analyticsService.getStudentExamPerformance(studentId);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/student/{studentId}/time-analysis")
    public ResponseEntity<List<Map<String, Object>>> getStudentTimeAnalysis(
            @PathVariable Long studentId,
            Authentication authentication) {

        List<Map<String, Object>> timeAnalysis = analyticsService.getStudentTimeAnalysis(studentId);
        return ResponseEntity.ok(timeAnalysis);
    }

    // NEW COMPREHENSIVE ENDPOINT
    @GetMapping("/student/{studentId}/course/{courseId}/comprehensive-report")
    public ResponseEntity<Map<String, Object>> getStudentComprehensiveReport(
            @PathVariable Long studentId,
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {

        // بررسی دسترسی معلم به دانش‌آموز و دوره
        User teacher = userService.findByUsername(authentication.getName());
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Access denied");
        }

        Map<String, Object> report = analyticsService.getStudentComprehensiveReport(studentId, courseId, days);
        return ResponseEntity.ok(report);
    }

    // ADD NEW ENDPOINTS FOR TEACHER ANALYTICS
    @GetMapping("/teacher/system-overview")
    public ResponseEntity<Map<String, Object>> getSystemOverview(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> overview = analyticsService.getSystemOverview(teacher);
        return ResponseEntity.ok(overview);
    }

    @GetMapping("/teacher/time-analysis")
    public ResponseEntity<List<Map<String, Object>>> getTimeAnalysis(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> timeAnalysis = analyticsService.getTimeAnalysis(teacher);
        return ResponseEntity.ok(timeAnalysis);
    }

    @GetMapping("/teacher/question-difficulty")
    public ResponseEntity<List<Map<String, Object>>> getQuestionDifficultyAnalysis(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> difficultyAnalysis = analyticsService.getQuestionDifficultyAnalysis(teacher);
        return ResponseEntity.ok(difficultyAnalysis);
    }

    @GetMapping("/teacher/lesson-performance")
    public ResponseEntity<List<Map<String, Object>>> getLessonPerformanceAnalysis(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> lessonPerformance = analyticsService.getLessonPerformanceAnalysis(teacher);
        return ResponseEntity.ok(lessonPerformance);
    }

    @GetMapping("/course/{courseId}/students-summary")
    public ResponseEntity<List<Map<String, Object>>> getCourseStudentsSummary(
            @PathVariable Long courseId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> studentsSummary = analyticsService.getCourseStudentsSummary(teacher, courseId);
        return ResponseEntity.ok(studentsSummary);
    }
}