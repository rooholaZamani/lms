package com.example.demo.dto;

import lombok.Data;

@Data
public class CategorizationItemDTO {
    private String text;
    private String correctCategory;
    private String itemType = "TEXT";
    private String mediaUrl;
    private Integer points;
}