package com.example.demo.dto;

import com.example.demo.model.QuestionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionDTO {
    private Long id;
    private String text;
    private QuestionType questionType;
    private Integer points;
    private String explanation;
    private String hint;
    private String template;
    private Integer timeLimit; // Time limit  in seconds
    private Boolean isRequired; // Fixed: was "Required" - should be "isRequired"
    private Double difficulty;

    // For multiple choice, true/false, and categorization questions
    private List<AnswerDTO> answers = new ArrayList<>();
    
    // For fill in the blanks questions
    private List<BlankAnswerDTO> blankAnswers = new ArrayList<>();
    
    // For matching questions
    private List<MatchingPairDTO> matchingPairs = new ArrayList<>();
    
    // For categorization questions - list of available categories
    private List<String> categories = new ArrayList<>();

    // For short answer questions - correct answer
    private String correctOption;
}