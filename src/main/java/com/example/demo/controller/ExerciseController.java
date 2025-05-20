package com.example.demo.controller;

import com.example.demo.dto.ExerciseDTO;
import com.example.demo.dto.ExerciseSubmissionDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.model.*;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.ExerciseService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;

    public ExerciseController(
            ExerciseService exerciseService,
            UserService userService,
            DTOMapperService dtoMapperService) {
        this.exerciseService = exerciseService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/lesson/{lessonId}")
    public ResponseEntity<ExerciseDTO> createExercise(
            @PathVariable Long lessonId,
            @RequestBody Exercise exercise,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Exercise savedExercise = exerciseService.createExercise(exercise, lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseDTO(savedExercise));
    }

    @GetMapping("/{exerciseId}")
    public ResponseEntity<ExerciseDTO> getExercise(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseService.getExerciseById(exerciseId);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseDTO(exercise));
    }

    @GetMapping("/lesson/{lessonId}")
    public ResponseEntity<ExerciseDTO> getExerciseByLesson(@PathVariable Long lessonId) {
        Exercise exercise = exerciseService.getExerciseByLessonId(lessonId);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseDTO(exercise));
    }

    @PostMapping("/{exerciseId}/questions")
    public ResponseEntity<QuestionDTO> addQuestion(
            @PathVariable Long exerciseId,
            @RequestBody Question question,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        Question savedQuestion = exerciseService.addQuestion(question, exerciseId);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(savedQuestion));
    }

    @GetMapping("/{exerciseId}/questions")
    public ResponseEntity<List<QuestionDTO>> getExerciseQuestions(@PathVariable Long exerciseId) {
        List<Question> questions = exerciseService.getExerciseQuestions(exerciseId);
        List<QuestionDTO> questionDTOs = questions.stream()
                .map(question -> dtoMapperService.mapToQuestionDTO(question))
                .collect(Collectors.toList());
        return ResponseEntity.ok(questionDTOs);
    }

    @PostMapping("/{exerciseId}/submit")
    public ResponseEntity<ExerciseSubmissionDTO> submitExercise(
            @PathVariable Long exerciseId,
            @RequestBody Map<Long, Long> answers,
            @RequestParam Map<Long, Integer> answerTimes,
            Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        ExerciseSubmission submission = exerciseService.submitExercise(exerciseId, student, answers, answerTimes);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseSubmissionDTO(submission));
    }

    @GetMapping("/submissions/student")
    public ResponseEntity<List<ExerciseSubmissionDTO>> getStudentSubmissions(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());
        List<ExerciseSubmission> submissions = exerciseService.getStudentSubmissions(student);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseSubmissionDTOList(submissions));
    }

    @GetMapping("/{exerciseId}/submissions")
    public ResponseEntity<List<ExerciseSubmissionDTO>> getExerciseSubmissions(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        User teacher = userService.findByUsername(authentication.getName());
        List<ExerciseSubmission> submissions = exerciseService.getExerciseSubmissions(exerciseId);
        return ResponseEntity.ok(dtoMapperService.mapToExerciseSubmissionDTOList(submissions));
    }

    @GetMapping("/{exerciseId}/difficulty")
    public ResponseEntity<Map<String, Object>> getExerciseDifficulty(@PathVariable Long exerciseId) {
        Map<String, Object> difficulty = exerciseService.calculateExerciseDifficulty(exerciseId);
        return ResponseEntity.ok(difficulty);
    }
}