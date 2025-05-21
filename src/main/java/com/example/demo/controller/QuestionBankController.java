package com.example.demo.controller;

import com.example.demo.dto.QuestionDTO;
import com.example.demo.model.Question;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.QuestionBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions/bank")
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final DTOMapperService dtoMapperService;

    public QuestionBankController(
            QuestionBankService questionBankService,
            DTOMapperService dtoMapperService) {
        this.questionBankService = questionBankService;
        this.dtoMapperService = dtoMapperService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionDTO>> getAllBankQuestions() {
        List<Question> questions = questionBankService.getAllBankQuestions();
        List<QuestionDTO> questionDTOs = questions.stream()
                .map(dtoMapperService::mapToQuestionDTO)
                .toList();
        return ResponseEntity.ok(questionDTOs);
    }

    @PostMapping
    public ResponseEntity<QuestionDTO> createBankQuestion(
            @RequestBody Question question,
            Authentication authentication) {
        Question savedQuestion = questionBankService.createBankQuestion(question);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(savedQuestion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionDTO> updateBankQuestion(
            @PathVariable Long id,
            @RequestBody Question question,
            Authentication authentication) {
        Question updatedQuestion = questionBankService.updateBankQuestion(id, question);
        return ResponseEntity.ok(dtoMapperService.mapToQuestionDTO(updatedQuestion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBankQuestion(
            @PathVariable Long id,
            Authentication authentication) {
        questionBankService.deleteBankQuestion(id);
        return ResponseEntity.ok().build();
    }
}