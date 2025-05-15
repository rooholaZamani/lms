package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.ExerciseRepository;
import com.example.demo.repository.ExerciseSubmissionRepository;
import com.example.demo.repository.LessonRepository;
import com.example.demo.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

        // Update lesson with exercise
        lesson.setExam(savedExercise);
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
        question.setExam(exercise);
        return questionRepository.save(question);
    }

    public List<Question> getExerciseQuestions(Long exerciseId) {
        Exercise exercise = getExerciseById(exerciseId);
        return questionRepository.findByExamOrderById(exercise);
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

        List<Question> questions = questionRepository.findByExamOrderById(exercise);
        for (Question question : questions) {
            totalPoints += question.getPoints();

            Long answerId = answers.get(question.getId());
            if (answerId != null) {
                // Check if correct answer
                boolean isCorrect = question.getAnswers().stream()
                        .filter(answer -> answer.getId().equals(answerId))
                        .findFirst()
                        .map(Answer::isCorrect)
                        .orElse(false);

                if (isCorrect) {
                    earnedPoints += question.getPoints();

                    // Calculate time bonus if applicable
                    Integer timeTaken = answerTimes.get(question.getId());
                    if (timeTaken != null && exercise.getTimeLimit() != null) {
                        // Example: bonus points for quick answers
                        int expectedTime = exercise.getTimeLimit() / questions.size();
                        if (timeTaken < expectedTime) {
                            // Award bonus based on how quickly they answered
                            int bonus = Math.min(question.getPoints() / 2,
                                    (expectedTime - timeTaken) / 5);
                            timeBonus += bonus;
                        }
                    }
                }
            }
        }

        submission.setScore(earnedPoints);
        submission.setTimeBonus(timeBonus);
        submission.setTotalScore(earnedPoints + timeBonus);
        submission.setPassed(earnedPoints >= exercise.getPassingScore());

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

        Map<String, Object> difficultyData = new HashMap<>();

        // Basic stats
        int totalSubmissions = submissions.size();
        if (totalSubmissions == 0) {
            difficultyData.put("difficulty", "Unknown (no submissions)");
            difficultyData.put("passRate", 0);
            difficultyData.put("averageScore", 0);
            return difficultyData;
        }

        // Calculate pass rate
        long passedCount = submissions.stream()
                .filter(ExerciseSubmission::isPassed)
                .count();
        double passRate = (double) passedCount / totalSubmissions * 100;

        // Calculate average score
        double averageScore = submissions.stream()
                .mapToDouble(ExerciseSubmission::getScore)
                .average()
                .orElse(0);

        // Calculate average time per question if time data exists
        Map<Long, List<Integer>> questionTimes = new HashMap<>();

        for (ExerciseSubmission submission : submissions) {
            for (Map.Entry<Long, Integer> entry : submission.getAnswerTimes().entrySet()) {
                Long questionId = entry.getKey();
                Integer time = entry.getValue();

                questionTimes.computeIfAbsent(questionId, k -> new ArrayList<>())
                        .add(time);
            }
        }

        // Calculate per-question difficulty
        List<Map<String, Object>> questionDifficulties = new ArrayList<>();
        List<Question> questions = questionRepository.findByExamOrderById(exercise);

        for (Question question : questions) {
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("questionId", question.getId());
            questionData.put("text", question.getText());

            // Count correct answers for this question
            long correctCount = submissions.stream()
                    .filter(s -> {
                        Long answerId = s.getAnswers().get(question.getId());
                        if (answerId == null) return false;

                        return question.getAnswers().stream()
                                .filter(a -> a.getId().equals(answerId) && a.isCorrect())
                                .findFirst()
                                .isPresent();
                    })
                    .count();

            double correctRate = (double) correctCount / totalSubmissions * 100;
            questionData.put("correctRate", correctRate);

            // Calculate average time for this question
            List<Integer> times = questionTimes.get(question.getId());
            if (times != null && !times.isEmpty()) {
                double avgTime = times.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0);
                questionData.put("averageTime", avgTime);
            }

            // Difficulty rating based on correct rate
            String difficulty;
            if (correctRate >= 80) difficulty = "Easy";
            else if (correctRate >= 50) difficulty = "Medium";
            else difficulty = "Hard";

            questionData.put("difficulty", difficulty);
            questionDifficulties.add(questionData);
        }

        // Overall exercise difficulty
        String overallDifficulty;
        if (passRate >= 80) overallDifficulty = "Easy";
        else if (passRate >= 50) overallDifficulty = "Medium";
        else overallDifficulty = "Hard";

        difficultyData.put("difficulty", overallDifficulty);
        difficultyData.put("passRate", passRate);
        difficultyData.put("averageScore", averageScore);
        difficultyData.put("totalSubmissions", totalSubmissions);
        difficultyData.put("questions", questionDifficulties);

        return difficultyData;
    }
}