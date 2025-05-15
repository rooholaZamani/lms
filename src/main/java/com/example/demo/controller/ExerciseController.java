package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.service.ExerciseService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final UserService userService;

    public ExerciseController(ExerciseService exerciseService, UserService userService) {
        this.exerciseService = exerciseService;
        this.userService = userService;
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<Exercise> createExercise(
            @PathVariable Long lessonId,
            @RequestBody Exercise exercise,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Exercise savedExercise = exerciseService.createExercise(exercise, lessonId);
        return ResponseEntity.ok(savedExercise);
    }

    @GetMapping("/{exerciseId}")
    public ResponseEntity<Exercise> getExercise(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseService.getExerciseById(exerciseId);
        return ResponseEntity.ok(exercise);
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<Exercise> getExerciseByLesson(@PathVariable Long lessonId) {
        Exercise exercise = exerciseService.getExerciseByLessonId(lessonId);
        return ResponseEntity.ok(exercise);
    }

    @PostMapping("/{exerciseId}/questions")
    public ResponseEntity<Question> addQuestion(
            @PathVariable Long exerciseId,
            @RequestBody Question question,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Question savedQuestion = exerciseService.addQuestion(question, exerciseId);
        return ResponseEntity.ok(savedQuestion);
    }

    @GetMapping("/{exerciseId}/questions")
    public ResponseEntity<List<Question>> getExerciseQuestions(@PathVariable Long exerciseId) {
        List<Question> questions = exerciseService.getExerciseQuestions(exerciseId);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/{exerciseId}/submit")
    public ResponseEntity<ExerciseSubmission> submitExercise(
            @PathVariable Long exerciseId,
            @RequestBody Map<Long, Long> answers,
            @RequestParam Map<Long, Integer> answerTimes,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        ExerciseSubmission submission = exerciseService.submitExercise(exerciseId, student, answers, answerTimes);
        return ResponseEntity.ok(submission);
    }

    @GetMapping("/submissions/student")
    public ResponseEntity<List<ExerciseSubmission>> getStudentSubmissions(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<ExerciseSubmission> submissions = exerciseService.getStudentSubmissions(student);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{exerciseId}/submissions")
    public ResponseEntity<List<ExerciseSubmission>> getExerciseSubmissions(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<ExerciseSubmission> submissions = exerciseService.getExerciseSubmissions(exerciseId);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/{exerciseId}/difficulty")
    public ResponseEntity<Map<String, Object>> getExerciseDifficulty(@PathVariable Long exerciseId) {
        Map<String, Object> difficulty = exerciseService.calculateExerciseDifficulty(exerciseId);
        return ResponseEntity.ok(difficulty);
    }
}