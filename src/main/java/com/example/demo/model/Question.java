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
    private QuestionType questionType;

    private Integer points;

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

    @ManyToOne
    private Exercise exercise;

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

    private Boolean inBank = false;

    private Integer timeLimit; // محدودیت زمان برای سوال (ثانیه)

    private Boolean isRequired = true; // آیا پاسخ دادن اجباری است

    private Double difficulty; // سطح دشواری (1.0 - 5.0)
}