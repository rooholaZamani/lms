package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class ChatMessageDTO {
    private Long id;
    private Long courseId;
    private String courseName;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime sentAt;
    private Set<Long> readBy;
}