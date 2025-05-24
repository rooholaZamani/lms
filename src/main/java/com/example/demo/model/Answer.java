package com.example.demo.model;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "answer") // تغییر نام جدول برای جلوگیری از تداخل
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(length = 1000)
    private String text;

    private Boolean correct = false;

    private String answerType = "TEXT"; // TEXT, IMAGE, AUDIO

    @Column(length = 1000)
    private String mediaUrl; // URL برای تصویر یا صوت

    private Integer points = 0; // امتیاز این گزینه (برای امتیازدهی جزئی)

    @Column(length = 500)
    private String feedback; // بازخورد برای این گزینه

    private Integer orderIndex; // ترتیب نمایش گزینه

    // برای سوالات دسته‌بندی
    @Column(length = 200)
    private String category; // دسته‌بندی
}