package com.example.demo.controller;

import com.example.demo.dto.LessonDTO;
import com.example.demo.model.Lesson;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.LessonService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private final LessonService lessonService;
    private final CourseService courseService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;

    public LessonController(
            LessonService lessonService,
            CourseService courseService,
            UserService userService,
            DTOMapperService dtoMapperService) {
        this.lessonService = lessonService;
        this.courseService = courseService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/course/{courseId}")
    public ResponseEntity<LessonDTO> createLesson(
            @PathVariable Long courseId,
            @RequestBody Lesson lesson,
            Authentication authentication) {

        // Verify teacher owns the course
        User teacher = userService.findByUsername(authentication.getName());
        courseService.getCourseById(courseId); // Will throw if not found

        // Save the lesson
        Lesson savedLesson = lessonService.createLesson(lesson, courseId);
        return ResponseEntity.ok(dtoMapperService.mapToLessonDTO(savedLesson));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<LessonDTO>> getCourseLessons(
            @PathVariable Long courseId) {

        List<Lesson> lessons = lessonService.getCourseLessons(courseId);
        return ResponseEntity.ok(dtoMapperService.mapToLessonDTOList(lessons));
    }

    @GetMapping("/{lessonId}")
    public ResponseEntity<LessonDTO> getLessonById(
            @PathVariable Long lessonId) {

        Lesson lesson = lessonService.getLessonById(lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToLessonDTO(lesson));
    }

    @PutMapping("/{lessonId}")
    public ResponseEntity<LessonDTO> updateLesson(
            @PathVariable Long lessonId,
            @RequestBody Lesson lessonDetails,
            Authentication authentication) {

        // Get the lesson
        Lesson lesson = lessonService.getLessonById(lessonId);

        // Verify teacher owns the course
        User teacher = userService.findByUsername(authentication.getName());

        // Update the lesson
        Lesson updatedLesson = lessonService.updateLesson(lessonId, lessonDetails);
        return ResponseEntity.ok(dtoMapperService.mapToLessonDTO(updatedLesson));
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
    @GetMapping("/teaching")
    public ResponseEntity<List<LessonDTO>> getTeacherLessons(Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());

        // Verify user is a teacher
        boolean isTeacher = teacher.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (!isTeacher) {
            throw new RuntimeException("Access denied: Only teachers can access this endpoint");
        }

        List<Lesson> lessons = lessonService.getTeacherLessons(teacher);
        List<LessonDTO> lessonDTOs = dtoMapperService.mapToLessonDTOList(lessons);

        return ResponseEntity.ok(lessonDTOs);
    }
}