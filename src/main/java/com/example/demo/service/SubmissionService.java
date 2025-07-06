package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;
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
        Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());
        
        List<Map<String, Object>> questionAnswers = questions.stream()
                .map(question -> buildQuestionAnswerData(question, studentAnswers))
                .collect(Collectors.toList());

        result.put("questionAnswers", questionAnswers);

        // آمار کلی
        long totalQuestions = questions.size();
        long correctAnswers = questionAnswers.stream()
                .mapToLong(qa -> (Boolean) qa.get("isCorrect") ? 1 : 0)
                .sum();
        long incorrectAnswers = questionAnswers.stream()
                .mapToLong(qa -> !(Boolean) qa.get("isCorrect") && qa.get("studentAnswer") != null ? 1 : 0)
                .sum();
        long unanswered = totalQuestions - correctAnswers - incorrectAnswers;

        result.put("totalQuestions", totalQuestions);
        result.put("correctAnswers", correctAnswers);
        result.put("incorrectAnswers", incorrectAnswers);
        result.put("unanswered", unanswered);

        return result;
    }

    private Map<String, Object> buildQuestionAnswerData(Question question, Map<String, Object> studentAnswers) {
        Map<String, Object> qaData = new HashMap<>();
        
        qaData.put("questionId", question.getId());
        qaData.put("questionText", question.getText());
        qaData.put("questionType", question.getQuestionType().toString());
        qaData.put("points", question.getPoints());

        // Get student answer
        String questionId = question.getId().toString();
        Object studentAnswer = studentAnswers.get(questionId);
        
        // Process answer based on question type
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                processSimpleAnswer(qaData, question, studentAnswer);
                break;
                
            case CATEGORIZATION:
                processCategorizationAnswer(qaData, question, studentAnswer);
                break;
                
            case MATCHING:
                processMatchingAnswer(qaData, question, studentAnswer);
                break;
                
            case FILL_IN_THE_BLANK:
                processFillBlankAnswer(qaData, question, studentAnswer);
                break;
                
            default:
                qaData.put("studentAnswer", "Unsupported question type");
                qaData.put("isCorrect", false);
                qaData.put("pointsEarned", 0);
        }

        return qaData;
    }

    private void processSimpleAnswer(Map<String, Object> qaData, Question question, Object studentAnswer) {
        if (studentAnswer != null) {
            Long answerId = Long.parseLong(studentAnswer.toString());
            
            Optional<Answer> answerOpt = question.getAnswers().stream()
                    .filter(a -> a.getId().equals(answerId))
                    .findFirst();

            if (answerOpt.isPresent()) {
                Answer answer = answerOpt.get();
                qaData.put("studentAnswerId", answerId);
                qaData.put("studentAnswerText", answer.getText());
                qaData.put("isCorrect", answer.getCorrect());
                qaData.put("pointsEarned", answer.getCorrect() ? question.getPoints() : 0);
            } else {
                qaData.put("studentAnswer", "Invalid answer ID");
                qaData.put("isCorrect", false);
                qaData.put("pointsEarned", 0);
            }
        } else {
            qaData.put("studentAnswer", null);
            qaData.put("isCorrect", false);
            qaData.put("pointsEarned", 0);
        }

        // Add correct answer info
        Optional<Answer> correctAnswer = question.getAnswers().stream()
                .filter(Answer::getCorrect)
                .findFirst();
        
        if (correctAnswer.isPresent()) {
            qaData.put("correctAnswerId", correctAnswer.get().getId());
            qaData.put("correctAnswerText", correctAnswer.get().getText());
        }

        // Add all options
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
    }

    private void processCategorizationAnswer(Map<String, Object> qaData, Question question, Object studentAnswer) {
        if (studentAnswer != null) {
            Map<String, String> studentCategories = parseComplexAnswer(studentAnswer.toString());
            qaData.put("studentAnswer", studentCategories);
            
            // Check correctness
            boolean isCorrect = true;
            Map<String, String> correctAnswers = new HashMap<>();
            
            for (Answer answer : question.getAnswers()) {
                correctAnswers.put(answer.getText(), answer.getCategory());
            }
            
            for (Map.Entry<String, String> entry : studentCategories.entrySet()) {
                String item = entry.getKey();
                String studentCategory = entry.getValue();
                String correctCategory = correctAnswers.get(item);
                
                if (!Objects.equals(studentCategory, correctCategory)) {
                    isCorrect = false;
                    break;
                }
            }
            
            qaData.put("isCorrect", isCorrect);
            qaData.put("pointsEarned", isCorrect ? question.getPoints() : 0);
        } else {
            qaData.put("studentAnswer", null);
            qaData.put("isCorrect", false);
            qaData.put("pointsEarned", 0);
        }

        // Add correct categorization
        Map<String, String> correctCategories = new HashMap<>();
        for (Answer answer : question.getAnswers()) {
            correctCategories.put(answer.getText(), answer.getCategory());
        }
        qaData.put("correctAnswer", correctCategories);
        
        // Add categories list
        qaData.put("categories", question.getCategories());
    }

    private void processMatchingAnswer(Map<String, Object> qaData, Question question, Object studentAnswer) {
        if (studentAnswer != null) {
            Map<String, String> studentMatches = parseComplexAnswer(studentAnswer.toString());
            qaData.put("studentAnswer", studentMatches);
            
            // For now, mark as correct if answer exists
            // You can implement proper matching validation here
            qaData.put("isCorrect", true);
            qaData.put("pointsEarned", question.getPoints());
        } else {
            qaData.put("studentAnswer", null);
            qaData.put("isCorrect", false);
            qaData.put("pointsEarned", 0);
        }
        
        // Add matching pairs info
        qaData.put("matchingPairs", "Implementation needed");
    }

    private void processFillBlankAnswer(Map<String, Object> qaData, Question question, Object studentAnswer) {
        if (studentAnswer != null) {
            String studentText = studentAnswer.toString().trim();
            qaData.put("studentAnswer", studentText);
            
            // Check if matches any correct answer
            boolean isCorrect = question.getAnswers().stream()
                    .anyMatch(answer -> answer.getText().trim().equalsIgnoreCase(studentText));
            
            qaData.put("isCorrect", isCorrect);
            qaData.put("pointsEarned", isCorrect ? question.getPoints() : 0);
        } else {
            qaData.put("studentAnswer", null);
            qaData.put("isCorrect", false);
            qaData.put("pointsEarned", 0);
        }

        // Add correct answers
        List<String> correctAnswers = question.getAnswers().stream()
                .map(Answer::getText)
                .collect(Collectors.toList());
        qaData.put("correctAnswers", correctAnswers);
    }

    private Map<String, Object> parseAnswersJson(String answersJson) {
        Map<String, Object> answers = new HashMap<>();
        
        if (answersJson == null || answersJson.trim().isEmpty()) {
            return answers;
        }
        
        try {
            // Simple JSON parsing
            if (answersJson.startsWith("{") && answersJson.endsWith("}")) {
                String content = answersJson.substring(1, answersJson.length() - 1);
                
                if (!content.trim().isEmpty()) {
                    String[] pairs = content.split(",(?=\\\"[^\\\"]*\\\":\\\"[^\\\"]*\\\")");
                    
                    for (String pair : pairs) {
                        String[] keyValue = pair.split(":", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim().replaceAll("\"", "");
                            String value = keyValue[1].trim();
                            
                            if (value.startsWith("{") && value.endsWith("}")) {
                                // Complex answer - keep as string for further parsing
                                answers.put(key, value);
                            } else {
                                // Simple answer
                                answers.put(key, value.replaceAll("\"", ""));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map if parsing fails
        }
        
        return answers;
    }

    private Map<String, String> parseComplexAnswer(String answerJson) {
        Map<String, String> result = new HashMap<>();
        
        if (answerJson.startsWith("{") && answerJson.endsWith("}")) {
            String content = answerJson.substring(1, answerJson.length() - 1);
            String[] pairs = content.split(",");
            
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    result.put(key, value);
                }
            }
        }
        
        return result;
    }
}