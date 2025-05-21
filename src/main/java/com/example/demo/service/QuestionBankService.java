package com.example.demo.service;

import com.example.demo.model.Question;
import com.example.demo.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionBankService {

    private final QuestionRepository questionRepository;

    public QuestionBankService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public List<Question> getAllBankQuestions() {
        return questionRepository.findByIsInBankTrue();
    }

    @Transactional
    public Question createBankQuestion(Question question) {
        question.setIsInBank(true);
        question.setExam(null);
        question.setExercise(null);
        return questionRepository.save(question);
    }

    @Transactional
    public Question updateBankQuestion(Long id, Question questionDetails) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank question not found"));

        // Verify it's a bank question
        if (!question.isInBank()) {
            throw new RuntimeException("Not a bank question");
        }

        // Update fields
        question.setText(questionDetails.getText());
        question.setPoints(questionDetails.getPoints());

        // Handle answers if needed
        if (questionDetails.getAnswers() != null && !questionDetails.getAnswers().isEmpty()) {
            question.getAnswers().clear();
            question.getAnswers().addAll(questionDetails.getAnswers());
        }

        return questionRepository.save(question);
    }

    @Transactional
    public void deleteBankQuestion(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank question not found"));

        if (!question.isInBank()) {
            throw new RuntimeException("Not a bank question");
        }

        questionRepository.delete(question);
    }
}