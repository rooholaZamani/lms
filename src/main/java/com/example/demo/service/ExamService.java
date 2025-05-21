// src/main/java/com/example/demo/service/ExamService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.LessonRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;

    public ExamService(
            ExamRepository examRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository) {
        this.examRepository = examRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public Exam createExam(Exam exam, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // Save exam first
        Exam savedExam = examRepository.save(exam);

        // Update lesson with exam
        lesson.setExam(savedExam);
        lessonRepository.save(lesson);

        return savedExam;
    }

    public Exam getExamById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
    }

    public Exam getExamByLessonId(Long lessonId) {
        return examRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new RuntimeException("Exam not found for this lesson"));
    }

    public Question addQuestion(Question question, Long examId) {
        Exam exam = getExamById(examId);
        question.setExam(exam);
        return questionRepository.save(question);
    }

    public List<Question> getExamQuestions(Long examId) {
        Exam exam = getExamById(examId);
        return questionRepository.findByExamOrderById(exam);
    }

    @Transactional
    public Submission submitExam(Long examId, User student, Map<Long, Long> answers) {
        Exam exam = getExamById(examId);

        Submission submission = new Submission();
        submission.setStudent(student);
        submission.setExam(exam);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setAnswers(answers);

        // Calculate score
        int totalPoints = 0;
        int earnedPoints = 0;

        List<Question> questions = questionRepository.findByExamOrderById(exam);
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
                }
            }
        }

        submission.setScore(earnedPoints);
        submission.setPassed(earnedPoints >= exam.getPassingScore());

        return submissionRepository.save(submission);
    }

    public List<Submission> getStudentSubmissions(User student) {
        return submissionRepository.findByStudent(student);
    }

    public List<Submission> getExamSubmissions(Long examId) {
        Exam exam = getExamById(examId);
        return submissionRepository.findByExam(exam);
    }
    @Transactional
    public Question cloneQuestionFromBank(Long examId, Long questionId) {
        Exam exam = getExamById(examId);
        Question bankQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Bank question not found"));

        if (!bankQuestion.getInBank()) {
            throw new RuntimeException("Not a bank question");
        }

        // Create a new question based on bank question
        Question newQuestion = new Question();
        newQuestion.setText(bankQuestion.getText());
        newQuestion.setPoints(bankQuestion.getPoints());
        newQuestion.setExam(exam);
        newQuestion.setInBank(false);

        // Clone answers
        for (Answer answer : bankQuestion.getAnswers()) {
            Answer newAnswer = new Answer();
            newAnswer.setText(answer.getText());
            newAnswer.setCorrect(answer.isCorrect());
            newQuestion.getAnswers().add(newAnswer);
        }

        return questionRepository.save(newQuestion);
    }
}