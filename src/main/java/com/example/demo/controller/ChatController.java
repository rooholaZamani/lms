package com.example.demo.controller;

import com.example.demo.dto.ChatMessageDTO;
import com.example.demo.model.ChatMessage;
import com.example.demo.model.Content;
import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final DTOMapperService dtoMapperService;
    private final ActivityTrackingService activityTrackingService;
    private final CourseService courseService;

    public ChatController(
            ChatService chatService,
            UserService userService,
            DTOMapperService dtoMapperService, ActivityTrackingService activityTrackingService, CourseService courseService) {
        this.chatService = chatService;
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
        this.activityTrackingService = activityTrackingService;
        this.courseService = courseService;
    }

    @PostMapping("/course/{courseId}/send")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @PathVariable Long courseId,
            @RequestParam("message") String messageContent,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // اضافه شد
            Authentication authentication) {
        User sender = userService.findByUsername(authentication.getName());
        ChatMessage message = chatService.sendMessage(courseId, sender, messageContent);

        Course course = courseService.getCourseById(courseId);
        Map<String, String> metadata = new HashMap<>();

        metadata.put("courseTitle", course.getTitle());

        activityTrackingService.logActivity(sender, "CHAT_MESSAGE_SEND", courseId, timeSpent,metadata);


        return ResponseEntity.ok(dtoMapperService.mapToChatMessageDTO(message));
    }

    @GetMapping("/course/{courseId}/messages")
    public ResponseEntity<List<ChatMessageDTO>> getCourseMessages(
            @PathVariable Long courseId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "timeSpent", required = false, defaultValue = "0") Long timeSpent, // اضافه شد
            Authentication authentication) { // اضافه شد

        // لاگ گیری مشاهده چت
        if (authentication != null) {
            User user = userService.findByUsername(authentication.getName());

            Course course = courseService.getCourseById(courseId);
            Map<String, String> metadata = new HashMap<>();

            metadata.put("courseTitle", course.getTitle());
            activityTrackingService.logActivity(user, "CHAT_VIEW", courseId, timeSpent,metadata);
            activityTrackingService.updateStudyTime(user, course,timeSpent);
        }

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