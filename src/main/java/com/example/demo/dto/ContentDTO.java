package com.example.demo.dto;


import com.example.demo.model.ContentType;
import lombok.Data;

@Data
public class ContentDTO {
    private Long id;
    private String title;
    private ContentType type;
    private String textContent;
    private FileMetadataDTO file;
    private Integer orderIndex;

}
