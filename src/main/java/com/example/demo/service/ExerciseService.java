package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.ExerciseRepository;
import com.example.demo.repository.ExerciseSubmissionRepository;
import com.example.demo.repository.LessonRepository;
import com.example.demo.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final ExerciseSubmissionRepository submissionRepository;

    public ExerciseService(
            ExerciseRepository exerciseRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            ExerciseSubmissionRepository submissionRepository) {
        this.exerciseRepository = exerciseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public Exercise createExercise(Exercise exercise, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // Save exercise first
        Exercise savedExercise = exerciseRepository.save(exercise);

        // Update lesson with exercise - here's where the type error might be occurring
        // Make sure lesson has a reference to the Exercise, not Exam
        lesson.setExercise(savedExercise);  // You'll need to add this field to Lesson
        lessonRepository.save(lesson);

        return savedExercise;
    }

    public Exercise getExerciseById(Long exerciseId) {
        return exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new RuntimeException("Exercise not found"));
    }

    public Exercise getExerciseByLessonId(Long lessonId) {
        return exerciseRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new RuntimeException("Exercise not found for this lesson"));
    }

    public Question addQuestion(Question question, Long exerciseId) {
        Exercise exercise = getExerciseById(exerciseId);
        question.setExercise(exercise);  // You'll need to add this field to Question model
        return questionRepository.save(question);
    }

    public List<Question> getExerciseQuestions(Long exerciseId) {
        Exercise exercise = getExerciseById(exerciseId);
        return questionRepository.findByExerciseOrderById(exercise);  // You'll need to add this method
    }

    @Transactional
    public ExerciseSubmission submitExercise(Long exerciseId, User student, Map<Long, Long> answers, Map<Long, Integer> answerTimes) {
        Exercise exercise = getExerciseById(exerciseId);

        ExerciseSubmission submission = new ExerciseSubmission();
        submission.setStudent(student);
        submission.setExercise(exercise);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setAnswers(answers);
        submission.setAnswerTimes(answerTimes);

        // Calculate score
        int totalPoints = 0;
        int earnedPoints = 0;
        int timeBonus = 0;

        List<Question> questions = getExerciseQuestions(exerciseId);
        for (Question question : questions) {
            totalPoints += question.getPoints();

            Long answerId = answers.get(question.getId());
            if (answerId != null) {
                // Check if correct answer
                boolean isCorrect = question.getAnswers().stream()
                        .filter(answer -> answer.getId().equals(answerId))
                        .findFirst()
                        .map(Answer::getCorrect)
                        .orElse(false);

                if (isCorrect) {
                    earnedPoints += question.getPoints();

                    // Add time bonus if applicable
                    Integer timeTaken = answerTimes.get(question.getId());
                    if (timeTaken != null && timeTaken < 30) {  // Example: bonus for < 30 seconds
                        timeBonus += question.getPoints() / 2;  // 50% bonus for fast answers
                    }
                }
            }
        }

        submission.setScore(earnedPoints);
        submission.setTimeBonus(timeBonus);
        submission.setTotalScore(earnedPoints + timeBonus);
        submission.setPassed(submission.getTotalScore() >= exercise.getPassingScore());

        return submissionRepository.save(submission);
    }

    public List<ExerciseSubmission> getStudentSubmissions(User student) {
        return submissionRepository.findByStudent(student);
    }

    public List<ExerciseSubmission> getExerciseSubmissions(Long exerciseId) {
        Exercise exercise = getExerciseById(exerciseId);
        return submissionRepository.findByExercise(exercise);
    }

    public Map<String, Object> calculateExerciseDifficulty(Long exerciseId) {
        Exercise exercise = getExerciseById(exerciseId);
        List<ExerciseSubmission> submissions = submissionRepository.findByExercise(exercise);

        Map<String, Object> difficultyMetrics = new HashMap<>();

        // Calculate overall pass rate
        int totalSubmissions = submissions.size();
        int passedCount = (int) submissions.stream().filter(ExerciseSubmission::isPassed).count();
        double passRate = totalSubmissions > 0 ? (double) passedCount / totalSubmissions * 100 : 0;

        // Calculate average score
        double averageScore = submissions.stream()
                .mapToDouble(ExerciseSubmission::getScore)
                .average()
                .orElse(0);

        // Calculate average time taken per question (if available)
        double averageTimePerQuestion = 0;
        int totalAnswerTimes = 0;
        int totalAnswers = 0;

        for (ExerciseSubmission submission : submissions) {
            for (Map.Entry<Long, Integer> answerTime : submission.getAnswerTimes().entrySet()) {
                totalAnswerTimes += answerTime.getValue();
                totalAnswers++;
            }
        }

        if (totalAnswers > 0) {
            averageTimePerQuestion = (double) totalAnswerTimes / totalAnswers;
        }

        // Calculate difficulty based on pass rate and average score
        double difficulty = 100 - passRate;

        // Question-level difficulty
        List<Map<String, Object>> questionDifficulty = getExerciseQuestions(exerciseId).stream()
                .map(question -> {
                    Map<String, Object> questionMetrics = new HashMap<>();
                    questionMetrics.put("questionId", question.getId());
                    questionMetrics.put("text", question.getText());

                    // Calculate how many students answered this question correctly
                    long correctCount = submissions.stream()
                            .filter(sub -> {
                                Long answerId = sub.getAnswers().get(question.getId());
                                if (answerId == null) return false;

                                return question.getAnswers().stream()
                                        .filter(answer -> answer.getId().equals(answerId))
                                        .findFirst()
                                        .map(Answer::getCorrect)
                                        .orElse(false);
                            })
                            .count();

                    double correctRate = totalSubmissions > 0 ? (double) correctCount / totalSubmissions * 100 : 0;
                    questionMetrics.put("correctRate", correctRate);
                    questionMetrics.put("difficulty", 100 - correctRate);

                    return questionMetrics;
                })
                .collect(Collectors.toList());

        // Populate result
        difficultyMetrics.put("exerciseId", exerciseId);
        difficultyMetrics.put("totalSubmissions", totalSubmissions);
        difficultyMetrics.put("passRate", passRate);
        difficultyMetrics.put("averageScore", averageScore);
        difficultyMetrics.put("averageTimePerQuestion", averageTimePerQuestion);
        difficultyMetrics.put("overallDifficulty", difficulty);
        difficultyMetrics.put("questionDifficulty", questionDifficulty);

        return difficultyMetrics;
    }
}