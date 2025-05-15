package com.example.demo.service;

import com.example.demo.model.ChatMessage;
import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.CourseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final CourseRepository courseRepository;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            CourseRepository courseRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Send a new message in a course chat
     */
    @Transactional
    public ChatMessage sendMessage(Long courseId, User sender, String content) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Verify user is either teacher or enrolled student
        boolean isTeacher = course.getTeacher().getId().equals(sender.getId());
        boolean isStudent = course.getEnrolledStudents().stream()
                .anyMatch(student -> student.getId().equals(sender.getId()));

        if (!isTeacher && !isStudent) {
            throw new RuntimeException("Unauthorized to send message in this course");
        }

        ChatMessage message = new ChatMessage();
        message.setCourse(course);
        message.setSender(sender);
        message.setContent(content);
        message.setSentAt(LocalDateTime.now());

        // Mark as read by sender
        message.getReadBy().add(sender.getId());

        return chatMessageRepository.save(message);
    }

    /**
     * Get messages for a course, paginated
     */
    public List<ChatMessage> getCourseMessages(Long courseId, int page, int size) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Pageable pageable = PageRequest.of(page, size);
        return chatMessageRepository.findByCourseOrderBySentAtDesc(course, pageable);
    }

    /**
     * Get count of unread messages for a user in a course
     */
    public Map<String, Object> getUnreadMessageCount(Long courseId, User user) {
        Map<String, Object> result = new HashMap<>();

        // Get count of unread messages
        Long unreadCount = chatMessageRepository.countUnreadMessages(courseId, user.getId());

        // Get first few unread messages for preview
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Pageable pageable = PageRequest.of(0, 3);
        List<ChatMessage> recentMessages = chatMessageRepository.findByCourseOrderBySentAtDesc(course, pageable);

        List<Map<String, Object>> messagePreview = recentMessages.stream()
                .filter(msg -> !msg.getReadBy().contains(user.getId()))
                .map(msg -> {
                    Map<String, Object> preview = new HashMap<>();
                    preview.put("id", msg.getId());
                    preview.put("sender", msg.getSender().getFirstName() + " " + msg.getSender().getLastName());
                    preview.put("content", msg.getContent().length() > 50 ?
                            msg.getContent().substring(0, 47) + "..." : msg.getContent());
                    preview.put("sentAt", msg.getSentAt());
                    return preview;
                })
                .collect(Collectors.toList());

        result.put("unreadCount", unreadCount);
        result.put("messagePreview", messagePreview);

        return result;
    }

    /**
     * Mark all messages in a course as read for a user
     */
    @Transactional
    public void markMessagesAsRead(Long courseId, User user) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Get unread messages
        Pageable pageable = PageRequest.of(0, 100); // Limit to reasonable batch size
        List<ChatMessage> messages = chatMessageRepository.findByCourseOrderBySentAtDesc(course, pageable);

        for (ChatMessage message : messages) {
            if (!message.getReadBy().contains(user.getId())) {
                message.getReadBy().add(user.getId());
                chatMessageRepository.save(message);
            }
        }
    }

    /**
     * Get list of chat participants for a course
     */
    public List<Map<String, Object>> getChatParticipants(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<Map<String, Object>> participants = new ArrayList<>();

        // Add teacher
        Map<String, Object> teacherInfo = new HashMap<>();
        User teacher = course.getTeacher();
        teacherInfo.put("id", teacher.getId());
        teacherInfo.put("name", teacher.getFirstName() + " " + teacher.getLastName());
        teacherInfo.put("role", "TEACHER");
        participants.add(teacherInfo);

        // Add students
        for (User student : course.getEnrolledStudents()) {
            Map<String, Object> studentInfo = new HashMap<>();
            studentInfo.put("id", student.getId());
            studentInfo.put("name", student.getFirstName() + " " + student.getLastName());
            studentInfo.put("role", "STUDENT");
            participants.add(studentInfo);
        }

        return participants;
    }
}