package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlankAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Integer blankIndex; // شماره جای خالی
    
    @Column(length = 500)
    private String correctAnswer; // پاسخ صحیح
    
    @Column(length = 1000)
    private String acceptableAnswers; // پاسخ‌های قابل قبول (JSON array)
    
    private Boolean caseSensitive = false; // حساس به حروف کوچک/بزرگ
    
    private Integer points = 1; // امتیاز این جای خالی
}