package com.example.demo.controller;

import com.example.demo.dto.ProgressDTO;
import com.example.demo.model.Content;
import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.repository.ContentRepository;
import com.example.demo.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final UserService userService;
    private final CourseService courseService;
    private final DTOMapperService dtoMapperService;
    private final ActivityTrackingService activityTrackingService;
    private final ContentService contentService;
    private final ContentRepository contentRepository;

    public ProgressController(
            ProgressService progressService,
            UserService userService,
            CourseService courseService,
            DTOMapperService dtoMapperService, ActivityTrackingService activityTrackingService, ContentService contentService, ContentRepository contentRepository) {
        this.progressService = progressService;
        this.userService = userService;
        this.courseService = courseService;
        this.dtoMapperService = dtoMapperService;
        this.activityTrackingService = activityTrackingService;
        this.contentService = contentService;
        this.contentRepository = contentRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProgressDTO>> getStudentProgress(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Progress> progressList = progressService.getProgressByStudent(student);

        List<ProgressDTO> progressDTOs = progressList.stream()
                .map(dtoMapperService::mapToProgressDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(progressDTOs);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getProgressStats(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Course> courses = courseService.getEnrolledCourses(student);
        List<Progress> progressList = progressService.getProgressByStudent(student);

        // Calculate overall stats for dashboard
        int totalCourses = courses.size();
        int completedCourses = 0;
        double averageProgress = 0;

        if (!progressList.isEmpty()) {
            for (Progress progress : progressList) {
                if (progress.getCompletionPercentage() >= 100) {
                    completedCourses++;
                }
                averageProgress += progress.getCompletionPercentage();
            }
            averageProgress = averageProgress / progressList.size();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCourses", totalCourses);
        stats.put("completedCourses", completedCourses);
        stats.put("averageProgress", (int)averageProgress);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ProgressDTO> getCourseProgress(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);
        Progress progress = progressService.getOrCreateProgress(student, course);
        
        // 🔥 FIXED: Use DTO instead of entity
        ProgressDTO progressDTO = dtoMapperService.mapToProgressDTO(progress);
        return ResponseEntity.ok(progressDTO);
    }

    @PostMapping("/lesson/{lessonId}/complete")
    public ResponseEntity<ProgressDTO> markLessonComplete(
            @PathVariable Long lessonId,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // ADD THIS
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markLessonComplete(student, lessonId);

        // ADD ACTIVITY TRACKING
        activityTrackingService.logActivity(student, "LESSON_COMPLETION", lessonId, timeSpent);
        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }

        ProgressDTO progressDTO = dtoMapperService.mapToProgressDTO(updatedProgress);
        return ResponseEntity.ok(progressDTO);
    }

    @PostMapping("/content/{contentId}/view")
    public ResponseEntity<ProgressDTO> markContentViewed(
            @PathVariable Long contentId,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent,
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markContentViewed(student, contentId);


        // بهبود لاگ گیری با اطلاعات تکمیلی
        Content content = contentService.getContentById(contentId);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("contentType", content.getType().toString()); // TEXT, VIDEO, PDF
        metadata.put("contentTitle", content.getTitle());
        metadata.put("lessonId", content.getLesson().getId().toString());
        metadata.put("lessonTitle", content.getLesson().getTitle());

        activityTrackingService.logActivity(student, "CONTENT_VIEW", contentId, timeSpent, metadata);

        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }

        ProgressDTO progressDTO = dtoMapperService.mapToProgressDTO(updatedProgress);
        return ResponseEntity.ok(progressDTO);
    }

    @PostMapping("/content/{contentId}/complete")
    public ResponseEntity<ProgressDTO> markContentComplete(
            @PathVariable Long contentId,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // ADD THIS
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markContentComplete(student, contentId);

        // ADD ACTIVITY TRACKING
        activityTrackingService.logActivity(student, "CONTENT_COMPLETION", contentId, timeSpent);
        if (timeSpent > 0) {
            activityTrackingService.updateStudyTime(student, timeSpent);
        }

        ProgressDTO progressDTO = dtoMapperService.mapToProgressDTO(updatedProgress);
        return ResponseEntity.ok(progressDTO);
    }
}