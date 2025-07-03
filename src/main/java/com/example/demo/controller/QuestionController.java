package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.ExamService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final ExamService examService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;

    public QuestionController(ExamService examService, UserService userService, DTOMapperService dtoMapperService) {
        this.examService = examService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/exam/{examId}")
    public ResponseEntity<QuestionDTO> addQuestionToExam(
            @PathVariable Long examId,
            @RequestBody QuestionRequestDTO questionRequest,
            Authentication authentication) {

        User teacher = userService.findByUsername(authentication.getName());
        Question question = convertDTOToQuestion(questionRequest);
        
        Question savedQuestion = examService.addQuestion(question, examId);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(savedQuestion));
    }


    private void convertCategorizationItems(QuestionRequestDTO dto, Question question) {
        // ❌ MISSING: Save categories list
        if (dto.getCategories() != null) {
            question.setCategories(new ArrayList<>(dto.getCategories()));
        }

        List<Answer> answers = new ArrayList<>();
        if (dto.getCategorizationItems() != null) {
            for (CategorizationItemDTO itemDTO : dto.getCategorizationItems()) {
                Answer answer = new Answer();
                answer.setText(itemDTO.getText());
                answer.setCategory(itemDTO.getCorrectCategory());
                answer.setAnswerType(itemDTO.getItemType());
                answer.setMediaUrl(itemDTO.getMediaUrl());
                answer.setPoints(itemDTO.getPoints());
                answer.setCorrect(true);
                answers.add(answer);
            }
        }
        question.setAnswers(answers);
    }
    private Question convertDTOToQuestion(QuestionRequestDTO dto) {
        Question question = new Question();
        question.setText(dto.getText());
        question.setQuestionType(dto.getQuestionType());
        question.setPoints(dto.getPoints());
        question.setExplanation(dto.getExplanation());
        question.setHint(dto.getHint());
        question.setTemplate(dto.getTemplate());
        question.setTimeLimit(dto.getTimeLimit());
        question.setIsRequired(dto.getIsRequired());
        question.setDifficulty(dto.getDifficulty());
        question.setInBank(false);

        switch (dto.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                convertOptionsToAnswers(dto, question);
                break;
                
            case FILL_IN_THE_BLANKS:
                convertBlankAnswers(dto, question);
                break;
                
            case MATCHING:
                convertMatchingPairs(dto, question);
                break;
                
            case CATEGORIZATION:
                convertCategorizationItems(dto, question);
                break;
                
            case ESSAY:
            case SHORT_ANSWER:
                // برای سوالات تشریحی نیازی به تنظیم خاصی نیست
                break;
        }

        return question;
    }

    private void convertOptionsToAnswers(QuestionRequestDTO dto, Question question) {
        List<Answer> answers = new ArrayList<>();
        
        if (dto.getOptions() != null) {
            for (AnswerOptionDTO optionDTO : dto.getOptions()) {
                Answer answer = new Answer();
                answer.setText(optionDTO.getText());
                answer.setCorrect(optionDTO.getCorrect());
                answer.setAnswerType(optionDTO.getAnswerType());
                answer.setMediaUrl(optionDTO.getMediaUrl());
                answer.setPoints(optionDTO.getPoints());
                answer.setFeedback(optionDTO.getFeedback());
                answer.setOrderIndex(optionDTO.getOrderIndex());
                answer.setCategory(optionDTO.getCategory());
                answers.add(answer);
            }
        }
        
        question.setAnswers(answers);
    }

    private void convertBlankAnswers(QuestionRequestDTO dto, Question question) {
        List<BlankAnswer> blankAnswers = new ArrayList<>();
        
        if (dto.getBlankAnswers() != null) {
            for (BlankAnswerDTO blankDTO : dto.getBlankAnswers()) {
                BlankAnswer blankAnswer = new BlankAnswer();
                blankAnswer.setBlankIndex(blankDTO.getBlankIndex());
                blankAnswer.setCorrectAnswer(blankDTO.getCorrectAnswer());
                blankAnswer.setCaseSensitive(blankDTO.getCaseSensitive());
                blankAnswer.setPoints(blankDTO.getPoints());
                
                // تبدیل لیست پاسخ‌های قابل قبول به JSON
                if (blankDTO.getAcceptableAnswers() != null) {
                    // TODO: تبدیل به JSON string
                    blankAnswer.setAcceptableAnswers(String.join(",", blankDTO.getAcceptableAnswers()));
                }
                
                blankAnswers.add(blankAnswer);
            }
        }
        
        question.setBlankAnswers(blankAnswers);
    }

    private void convertMatchingPairs(QuestionRequestDTO dto, Question question) {
        List<MatchingPair> matchingPairs = new ArrayList<>();
        
        if (dto.getMatchingPairs() != null) {
            for (MatchingPairDTO pairDTO : dto.getMatchingPairs()) {
                MatchingPair pair = new MatchingPair();
                pair.setLeftItem(pairDTO.getLeftItem());
                pair.setRightItem(pairDTO.getRightItem());
                pair.setLeftItemType(pairDTO.getLeftItemType());
                pair.setRightItemType(pairDTO.getRightItemType());
                pair.setLeftItemUrl(pairDTO.getLeftItemUrl());
                pair.setRightItemUrl(pairDTO.getRightItemUrl());
                pair.setPoints(pairDTO.getPoints());
                matchingPairs.add(pair);
            }
        }
        
        question.setMatchingPairs(matchingPairs);
    }
}