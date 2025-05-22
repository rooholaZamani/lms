package com.example.demo.dto;

import com.example.demo.model.QuestionType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class StudentAnswerDTO {
    private Long questionId;
    private QuestionType questionType;
    
    // برای سوالات چند گزینه‌ای
    private List<Long> selectedOptionIds;
    
    // برای سوالات تشریحی
    private String essayAnswer;
    
    // برای سوالات جای خالی
    private Map<Integer, String> blankAnswers; // blankIndex -> answer
    
    // برای سوالات جورکردن
    private Map<String, String> matchingAnswers; // leftItem -> rightItem
    
    // برای سوالات دسته‌بندی
    private Map<String, String> categorizationAnswers; // item -> category
    
    private Long timeSpent; // زمان صرف شده (میلی‌ثانیه)
}