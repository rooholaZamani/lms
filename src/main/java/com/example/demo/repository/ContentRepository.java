// src/main/java/com/example/demo/repository/ContentRepository.java
package com.example.demo.repository;

import com.example.demo.model.Content;
import com.example.demo.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Content, Long> {
    List<Content> findByLessonOrderByOrderIndex(Lesson lesson);
    List<Content> findByLessonIdOrderByOrderIndex(Long lessonId);
    Content findById(long id);
    Optional<Content> findByFileId(Long fileId);
}