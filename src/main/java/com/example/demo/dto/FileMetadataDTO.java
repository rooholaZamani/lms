package com.example.demo.dto;


import lombok.Data;

@Data
public class FileMetadataDTO {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
}
