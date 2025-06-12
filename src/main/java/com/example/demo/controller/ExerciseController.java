package com.example.demo.controller;

import com.example.demo.dto.ExerciseDTO;
import com.example.demo.dto.ExerciseSubmissionDTO;
import com.example.demo.dto.QuestionDTO;
import com.example.demo.model.*;
import com.example.demo.service.ActivityTrackingService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.ExerciseService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    private final ActivityTrackingService activityTrackingService;

    public ExerciseController(
            ExerciseService exerciseService,
            UserService userService,
            DTOMapperService dtoMapperService, ActivityTrackingService activityTrackingService) {
        this.exerciseService = exerciseService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
        this.activityTrackingService = activityTrackingService;
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
    public ResponseEntity<ExerciseDTO> getExercise(
            @PathVariable Long exerciseId,
            Authentication authentication) { // ADD THIS

        // ADD ACTIVITY TRACKING FOR EXERCISE ACCESS
        if (authentication != null) {
            User user = userService.findByUsername(authentication.getName());
            activityTrackingService.logActivity(user, "EXERCISE_START", exerciseId, 0L);
        }

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
            @RequestBody Map<String, Object> submissionData, // CHANGE THIS
            Authentication authentication) {

        User student = userService.findByUsername(authentication.getName());

        // EXTRACT DATA FROM REQUEST
        @SuppressWarnings("unchecked")
        Map<Long, Long> answers = (Map<Long, Long>) submissionData.get("answers");
        @SuppressWarnings("unchecked")
        Map<Long, Integer> answerTimes = (Map<Long, Integer>) submissionData.get("answerTimes");

        Long totalTimeSpent = answerTimes != null ?
                answerTimes.values().stream().mapToLong(Integer::longValue).sum() : 0L;

        ExerciseSubmission submission = exerciseService.submitExercise(exerciseId, student, answers, answerTimes);

        // ADD ACTIVITY TRACKING
        activityTrackingService.logActivity(student, "EXERCISE_SUBMISSION", exerciseId, totalTimeSpent);
        if (totalTimeSpent > 0) {
            activityTrackingService.updateStudyTime(student, totalTimeSpent);
        }

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
    @GetMapping("/available")
    @Operation(
            summary = "Get available exercises",
            description = "Get all exercises available for the authenticated student"
    )
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<ExerciseDTO>> getAvailableExercises(Authentication authentication) {
        User student = userService.findByUsername(authentication.getName());

        // Verify user is a student
        boolean isStudent = student.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        if (!isStudent) {
            throw new RuntimeException("Access denied: Only students can access available exercises");
        }

        List<Exercise> availableExercises = exerciseService.getAvailableExercisesForStudent(student);

        List<ExerciseDTO> exerciseDTOs = availableExercises.stream()
                .map(dtoMapperService::mapToExerciseDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(exerciseDTOs);
    }
}