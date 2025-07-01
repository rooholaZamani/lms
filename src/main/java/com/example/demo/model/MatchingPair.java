package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingPair {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(length = 500)
    private String leftItem; // آیتم سمت چپ
    
    @Column(length = 500)
    private String rightItem; // آیتم سمت راست
    
    private String leftItemType = "TEXT"; // TEXT, IMAGE, AUDIO
    private String rightItemType = "TEXT"; // TEXT, IMAGE, AUDIO
    
    @Column(length = 2000)
    private String leftItemUrl; // URL برای تصویر یا صوت
    
    @Column(length = 2000)
    private String rightItemUrl; // URL برای تصویر یا صوت
    
    private Integer points = 1; // امتیاز این جفت
}