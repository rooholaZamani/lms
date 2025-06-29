package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE; // Set default value

    private Integer points = 1; // Set default value

    @Column(length = 1000)
    private String explanation; // توضیح سوال

    @Column(length = 2000) 
    private String hint; // راهنمایی

    // برای سوالات جای خالی - متن با {} برای نشان دادن جای خالی
    @Column(length = 2000)
    private String template;

    // برای سوالات matching و categorization - JSON format
    @Column(length = 5000)
    private String questionData;

    @ManyToOne
    private Exam exam;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    private List<Answer> answers = new ArrayList<>();

    // برای سوالات fill in the blanks
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id")
    private List<BlankAnswer> blankAnswers = new ArrayList<>();

    // برای سوالات matching
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "question_id") 
    private List<MatchingPair> matchingPairs = new ArrayList<>();

    @Column(name = "in_bank")
    private Boolean inBank = false;

    private Integer timeLimit; // محدودیت زمان برای سوال (ثانیه)

    @Column(name = "is_required")
    private Boolean isRequired = true; // آیا پاسخ دادن اجباری است

    private Double difficulty; // سطح دشواری (1.0 - 5.0)

    // Helper method to safely get question type
    public QuestionType getQuestionType() {
        return questionType != null ? questionType : QuestionType.MULTIPLE_CHOICE;
    }

    // PrePersist to ensure questionType is never null
    @PrePersist
    @PreUpdate
    private void validateQuestionType() {
        if (this.questionType == null) {
            this.questionType = QuestionType.MULTIPLE_CHOICE;
        }
        if (this.points == null) {
            this.points = 1;
        }
        if (this.inBank == null) {
            this.inBank = false;
        }
        if (this.isRequired == null) {
            this.isRequired = true;
        }
    }
}