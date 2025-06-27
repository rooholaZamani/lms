package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.CourseRepository;
import com.example.demo.service.AnalyticsService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final CourseRepository courseRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserService userService, CourseRepository courseRepository) {
        this.analyticsService = analyticsService;
        this.userService = userService;
        this.courseRepository = courseRepository;
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
    public ResponseEntity<Map<String, Object>> getStudentDetailedAnalysis(
            @PathVariable Long studentId,
            @PathVariable Long courseId,
            Authentication authentication) {

        Map<String, Object> analysis = analyticsService.getStudentDetailedAnalysis(studentId, courseId);
        return ResponseEntity.ok(analysis);
    }














    @GetMapping("/teacher/engagement-trends")
    public ResponseEntity<List<Map<String, Object>>> getEngagementTrends(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> trends = analyticsService.getEngagementTrends(teacher);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/teacher/challenging-questions")
    public ResponseEntity<List<Map<String, Object>>> getChallengingQuestions(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> questions = analyticsService.getChallengingQuestions(teacher);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/teacher/daily-engagement")
    public ResponseEntity<Map<String, Object>> getDailyEngagementStats(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> stats = analyticsService.getDailyEngagementStats(teacher);
        return ResponseEntity.ok(stats);
    }
    @GetMapping("/course/{courseId}/exam-scores")
    public ResponseEntity<Map<String, Object>> getCourseExamScores(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Long examId,
            @RequestParam(defaultValue = "false") boolean includeDetails,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> scores = analyticsService.getCourseExamScores(courseId, period, examId, includeDetails);
        return ResponseEntity.ok(scores);
    }

    @GetMapping("/course/{courseId}/time-distribution")
    public ResponseEntity<Map<String, Object>> getCourseTimeDistribution(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "daily") String granularity,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> timeDistribution = analyticsService.getCourseTimeDistribution(courseId, period, granularity);
        return ResponseEntity.ok(timeDistribution);
    }

    @GetMapping("/course/{courseId}/activity-stats")
    public ResponseEntity<Map<String, Object>> getCourseActivityStats(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(defaultValue = "false") boolean includeTimeline,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> activityStats = analyticsService.getCourseActivityStats(courseId, period, includeTimeline);
        return ResponseEntity.ok(activityStats);
    }
    @GetMapping("/course/{courseId}/lesson-progress")
    public ResponseEntity<Map<String, Object>> getCourseLessonProgress(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "month") String period,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Map<String, Object> lessonProgress = analyticsService.getCourseLessonProgress(courseId, period);
        return ResponseEntity.ok(lessonProgress);
    }
    @GetMapping("/teacher/students-progress")
    @Operation(summary = "Get students progress overview", description = "Get progress statistics for all students in teacher's courses")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> getStudentsProgressOverview(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can access this endpoint");
        }

        Map<String, Object> progressOverview = analyticsService.getStudentsProgressOverview(teacher);
        return ResponseEntity.ok(progressOverview);
    }

    @GetMapping("/teacher/student/{studentId}/performance")
    @Operation(summary = "Get specific student performance", description = "Get detailed performance analysis for a specific student")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> getStudentPerformanceForTeacher(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can access this endpoint");
        }

        Map<String, Object> studentPerformance = analyticsService.getStudentPerformanceForTeacher(teacher, studentId, courseId);
        return ResponseEntity.ok(studentPerformance);
    }

    @GetMapping("/teacher/course/{courseId}/students-summary")
    @Operation(summary = "Get students summary for a course", description = "Get summary of all students progress in a specific course")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<Map<String, Object>>> getCourseStudentsSummary(
            @PathVariable Long courseId,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        List<Map<String, Object>> studentsSummary = analyticsService.getCourseStudentsSummary(teacher, courseId);
        return ResponseEntity.ok(studentsSummary);
    }




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
}