package com.example.demo.repository;

import com.example.demo.model.ChatMessage;
import com.example.demo.model.Course;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByCourseOrderBySentAtDesc(Course course, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.course.id = ?1 AND ?2 NOT MEMBER OF m.readBy")
    Long countUnreadMessages(Long courseId, Long userId);
}