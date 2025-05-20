package com.example.demo.controller;

import com.example.demo.dto.ChatMessageDTO;
import com.example.demo.model.ChatMessage;
import com.example.demo.model.User;
import com.example.demo.service.ChatService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;

    public ChatController(
            ChatService chatService,
            UserService userService,
            DTOMapperService dtoMapperService) {
        this.chatService = chatService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @PostMapping("/course/{courseId}/send")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @PathVariable Long courseId,
            @RequestParam("message") String messageContent,
            Authentication authentication) {
        User sender = userService.findByUsername(authentication.getName());
        ChatMessage message = chatService.sendMessage(courseId, sender, messageContent);
        return ResponseEntity.ok(dtoMapperService.mapToChatMessageDTO(message));
    }

    @GetMapping("/course/{courseId}/messages")
    public ResponseEntity<List<ChatMessageDTO>> getCourseMessages(
            @PathVariable Long courseId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<ChatMessage> messages = chatService.getCourseMessages(courseId, page, size);
        return ResponseEntity.ok(dtoMapperService.mapToChatMessageDTOList(messages));
    }

    // Methods that return Maps can stay as they are for now
    @GetMapping("/course/{courseId}/unread")
    public ResponseEntity<Map<String, Object>> getUnreadMessageCount(
            @PathVariable Long courseId,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        Map<String, Object> unreadInfo = chatService.getUnreadMessageCount(courseId, user);
        return ResponseEntity.ok(unreadInfo);
    }

    @PostMapping("/course/{courseId}/mark-read")
    public ResponseEntity<?> markMessagesAsRead(
            @PathVariable Long courseId,
            Authentication authentication) {
        User user = userService.findByUsername(authentication.getName());
        chatService.markMessagesAsRead(courseId, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/course/{courseId}/participants")
    public ResponseEntity<List<Map<String, Object>>> getChatParticipants(@PathVariable Long courseId) {
        List<Map<String, Object>> participants = chatService.getChatParticipants(courseId);
        return ResponseEntity.ok(participants);
    }
}