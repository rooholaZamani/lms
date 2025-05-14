package com.example.demo.controller;

import com.example.demo.model.Lesson;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.LessonService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lessons")
@CrossOrigin(origins = "*")
public class LessonController {

    private final LessonService lessonService;
    private final CourseService courseService;
    private final UserService userService;

    public LessonController(
            LessonService lessonService,
            CourseService courseService,
            UserService userService) {
        this.lessonService = lessonService;
        this.courseService = courseService;
        this.userService = userService;
    }

    @PostMapping("/course/{courseId}")
    public ResponseEntity<Lesson> createLesson(
            @PathVariable Long courseId,
            @RequestBody Lesson lesson,
            Authentication authentication) {

        // Verify teacher owns the course
        User teacher = userService.findByUsername(authentication.getName());
        courseService.getCourseById(courseId); // Will throw if not found

        // Save the lesson
        Lesson savedLesson = lessonService.createLesson(lesson, courseId);
        return ResponseEntity.ok(savedLesson);
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<Lesson>> getCourseLessons(
            @PathVariable Long courseId) {

        List<Lesson> lessons = lessonService.getCourseLessons(courseId);
        return ResponseEntity.ok(lessons);
    }

    @GetMapping("/{lessonId}")
    public ResponseEntity<Lesson> getLessonById(
            @PathVariable Long lessonId) {

        Lesson lesson = lessonService.getLessonById(lessonId);
        return ResponseEntity.ok(lesson);
    }

    @PutMapping("/{lessonId}")
    public ResponseEntity<Lesson> updateLesson(
            @PathVariable Long lessonId,
            @RequestBody Lesson lessonDetails,
            Authentication authentication) {

        // Get the lesson
        Lesson lesson = lessonService.getLessonById(lessonId);

        // Verify teacher owns the course
        User teacher = userService.findByUsername(authentication.getName());

        // Update the lesson
        Lesson updatedLesson = lessonService.updateLesson(lessonId, lessonDetails);
        return ResponseEntity.ok(updatedLesson);
    }

    @DeleteMapping("/{lessonId}")
    public ResponseEntity<?> deleteLesson(
            @PathVariable Long lessonId,
            Authentication authentication) {

        // Get the lesson
        Lesson lesson = lessonService.getLessonById(lessonId);

        // Verify teacher owns the course
        User teacher = userService.findByUsername(authentication.getName());

        // Delete the lesson
        lessonService.deleteLesson(lessonId);
        return ResponseEntity.ok().build();
    }
}