package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            QuestionRepository questionRepository,
            ExamRepository examRepository) {
        this.submissionRepository = submissionRepository;
        this.questionRepository = questionRepository;
        this.examRepository = examRepository;
    }

    /**
     * دریافت جزئیات submission همراه با پاسخ‌های ارائه شده
     */
    public Map<String, Object> getSubmissionWithAnswers(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        Map<String, Object> result = new HashMap<>();
        
        // اطلاعات پایه submission
        result.put("id", submission.getId());
        result.put("studentId", submission.getStudent().getId());
        result.put("studentName", submission.getStudent().getFirstName() + " " + submission.getStudent().getLastName());
        result.put("score", submission.getScore());
        result.put("passed", submission.isPassed());
        result.put("submissionTime", submission.getSubmissionTime());
        result.put("timeSpent", submission.getTimeSpent());

        // اطلاعات آزمون
        Exam exam = submission.getExam();
        result.put("examId", exam.getId());
        result.put("examTitle", exam.getTitle());
        result.put("passingScore", exam.getPassingScore());

        // سوالات و پاسخ‌ها
        List<Question> questions = questionRepository.findByExamOrderById(exam);
        List<Map<String, Object>> questionAnswers = questions.stream()
                .map(question -> {
                    Map<String, Object> qaData = new HashMap<>();
                    qaData.put("questionId", question.getId());
                    qaData.put("questionText", question.getText());
                    qaData.put("points", question.getPoints());

                    // پاسخ دانش‌آموز
                    Long answerId = submission.getAnswers().get(question.getId());
                    if (answerId != null) {
                        Optional<Answer> answerOpt = question.getAnswers().stream()
                                .filter(a -> a.getId().equals(answerId))
                                .findFirst();

                        if (answerOpt.isPresent()) {
                            Answer answer = answerOpt.get();
                            qaData.put("studentAnswerId", answerId);
                            qaData.put("studentAnswerText", answer.getText());
                            qaData.put("isCorrect", answer.getCorrect());
                            qaData.put("pointsEarned", answer.getCorrect() ? question.getPoints() : 0);
                        }
                    } else {
                        qaData.put("studentAnswerId", null);
                        qaData.put("studentAnswerText", "No answer provided");
                        qaData.put("isCorrect", false);
                        qaData.put("pointsEarned", 0);
                    }

                    // پاسخ صحیح
                    Optional<Answer> correctAnswer = question.getAnswers().stream()
                            .filter(Answer::getCorrect)
                            .findFirst();
                    
                    if (correctAnswer.isPresent()) {
                        qaData.put("correctAnswerId", correctAnswer.get().getId());
                        qaData.put("correctAnswerText", correctAnswer.get().getText());
                    }

                    // همه گزینه‌ها (برای مرور کامل)
                    List<Map<String, Object>> options = question.getAnswers().stream()
                            .map(ans -> {
                                Map<String, Object> option = new HashMap<>();
                                option.put("id", ans.getId());
                                option.put("text", ans.getText());
                                option.put("isCorrect", ans.getCorrect());
                                return option;
                            })
                            .collect(Collectors.toList());
                    qaData.put("options", options);

                    return qaData;
                })
                .collect(Collectors.toList());

        result.put("questionAnswers", questionAnswers);

        // آمار کلی
        long totalQuestions = questions.size();
        long correctAnswers = questionAnswers.stream()
                .mapToLong(qa -> (Boolean) qa.get("isCorrect") ? 1 : 0)
                .sum();

        result.put("totalQuestions", totalQuestions);
        result.put("correctAnswers", correctAnswers);
        result.put("incorrectAnswers", totalQuestions - correctAnswers);
        result.put("accuracyPercentage", totalQuestions > 0 ? 
                Math.round((double) correctAnswers / totalQuestions * 100.0 * 10.0) / 10.0 : 0.0);

        return result;
    }

    /**
     * بررسی دسترسی دانش‌آموز به submission
     */
    public boolean hasAccessToSubmission(User user, Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // فقط صاحب submission یا معلم آزمون دسترسی دارند
        boolean isOwner = submission.getStudent().getId().equals(user.getId());
        boolean isTeacher = submission.getExam().getLesson().getCourse().getTeacher().getId().equals(user.getId());

        return isOwner || isTeacher;
    }

    /**
     * دریافت آمار submission برای معلم
     */
    public Map<String, Object> getSubmissionStatistics(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        List<Submission> submissions = submissionRepository.findByExam(exam);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubmissions", submissions.size());
        stats.put("passedSubmissions", submissions.stream().filter(Submission::isPassed).count());
        stats.put("averageScore", submissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0));

        // توزیع نمرات
        Map<String, Integer> scoreDistribution = new HashMap<>();
        scoreDistribution.put("0-25%", 0);
        scoreDistribution.put("26-50%", 0);
        scoreDistribution.put("51-75%", 0);
        scoreDistribution.put("76-100%", 0);

        for (Submission submission : submissions) {
            double percentage = (double) submission.getScore() / exam.getTotalPossibleScore() * 100;
            if (percentage <= 25) scoreDistribution.merge("0-25%", 1, Integer::sum);
            else if (percentage <= 50) scoreDistribution.merge("26-50%", 1, Integer::sum);
            else if (percentage <= 75) scoreDistribution.merge("51-75%", 1, Integer::sum);
            else scoreDistribution.merge("76-100%", 1, Integer::sum);
        }

        stats.put("scoreDistribution", scoreDistribution);

        return stats;
    }
}