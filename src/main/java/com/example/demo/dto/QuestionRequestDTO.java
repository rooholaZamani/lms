package com.example.demo.dto;

import com.example.demo.model.QuestionType;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QuestionRequestDTO {
    private String text;
    private QuestionType questionType;
    private Integer points;
    private String explanation;
    private String hint;
    private String template; // برای fill in the blanks
    private Integer timeLimit; // Time limit  in seconds
    private Boolean isRequired = true;
    private Double difficulty;
    
    // برای سوالات چند گزینه‌ای و صحیح/غلط
    private List<AnswerOptionDTO> options;
    
    // برای سوالات جای خالی
    private List<BlankAnswerDTO> blankAnswers;
    
    // برای سوالات جورکردن
    private List<MatchingPairDTO> matchingPairs;
    
    // برای سوالات دسته‌بندی
    private List<String> categories;
    private List<CategorizationItemDTO> categorizationItems;
}