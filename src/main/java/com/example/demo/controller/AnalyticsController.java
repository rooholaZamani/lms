package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.service.AnalyticsService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    public AnalyticsController(AnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping("/student/performance")
    public ResponseEntity<Map<String, Object>> getStudentPerformance(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Map<String, Object> performance = analyticsService.getStudentPerformance(student);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/course/{courseId}/student-comparison")
    public ResponseEntity<Map<String, Object>> getStudentComparison(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Map<String, Object> comparison = analyticsService.getStudentComparison(student, courseId);
        return ResponseEntity.ok(comparison);
    }

    @GetMapping("/course/{courseId}/top-performers")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getTopPerformers(@PathVariable Long courseId) {
        Map<String, List<Map<String, Object>>> topPerformers = analyticsService.getTopPerformers(courseId);
        return ResponseEntity.ok(topPerformers);
    }

    @GetMapping("/teacher/course/{courseId}/performance")
    public ResponseEntity<Map<String, Object>> getCoursePerformanceForTeacher(
            @PathVariable Long courseId,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> coursePerformance = analyticsService.getCoursePerformanceForTeacher(courseId);
        return ResponseEntity.ok(coursePerformance);
    }

    @GetMapping("/teacher/course/{courseId}/difficult-lessons")
    public ResponseEntity<List<Map<String, Object>>> getDifficultLessons(@PathVariable Long courseId) {
        List<Map<String, Object>> difficultLessons = analyticsService.getDifficultLessons(courseId);
        return ResponseEntity.ok(difficultLessons);
    }

    @GetMapping("/teacher/course/{courseId}/struggling-students")
    public ResponseEntity<List<Map<String, Object>>> getStrugglingStudents(@PathVariable Long courseId) {
        List<Map<String, Object>> strugglingStudents = analyticsService.getStrugglingStudents(courseId);
        return ResponseEntity.ok(strugglingStudents);
    }

    @GetMapping("/teacher/course/{courseId}/participation")
    public ResponseEntity<List<Map<String, Object>>> getParticipationMetrics(@PathVariable Long courseId) {
        List<Map<String, Object>> participationMetrics = analyticsService.getParticipationMetrics(courseId);
        return ResponseEntity.ok(participationMetrics);
    }

    @GetMapping("/student/exam/{examId}/details")
    public ResponseEntity<Map<String, Object>> getExamDetails(
            @PathVariable Long examId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Map<String, Object> examDetails = analyticsService.getExamDetails(student, examId);
        return ResponseEntity.ok(examDetails);
    }
    @GetMapping("/student/{studentId}/course/{courseId}/detailed")
    @GetMapping("/student/{studentId}/activity-timeline")
    @GetMapping("/student/{studentId}/exam-performance")
    @GetMapping("/student/{studentId}/time-analysis")
    @GetMapping("/teacher/system-overview")
    @GetMapping("/teacher/time-analysis")
    @GetMapping("/teacher/question-difficulty")
    @GetMapping("/teacher/lesson-performance")
    @GetMapping("/teacher/engagement-trends")
    @GetMapping("/teacher/challenging-questions")
    @GetMapping("/teacher/daily-engagement")
}