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
import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final UserService userService;
    private final CourseService courseService;

    public ProgressController(
            ProgressService progressService,
            UserService userService,
            CourseService courseService) {
        this.progressService = progressService;
        this.userService = userService;
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<List<Progress>> getStudentProgress(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<Progress> progressList = progressService.getProgressByStudent(student);
        return ResponseEntity.ok(progressList);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<Progress> getCourseProgress(
            @PathVariable Long courseId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);
        Progress progress = progressService.getOrCreateProgress(student, course);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/lesson/{lessonId}/complete")
    public ResponseEntity<Progress> markLessonComplete(
            @PathVariable Long lessonId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markLessonComplete(student, lessonId);
        return ResponseEntity.ok(updatedProgress);
    }

    @PostMapping("/content/{contentId}/view")
    public ResponseEntity<Progress> markContentViewed(
            @PathVariable Long contentId,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        Progress updatedProgress = progressService.markContentViewed(student, contentId);
        return ResponseEntity.ok(updatedProgress);
    }
}