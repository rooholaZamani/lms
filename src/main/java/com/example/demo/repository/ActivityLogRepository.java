package com.example.demo.repository;

import com.example.demo.model.ActivityLog;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository  // Add this annotation
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByUserAndTimestampBetweenOrderByTimestampDesc(User user, LocalDateTime start, LocalDateTime end);
    List<ActivityLog> findByActivityTypeAndTimestampBetween(String activityType, LocalDateTime start, LocalDateTime end);
    List<ActivityLog> findByUserAndActivityTypeAndTimestampBetween(User user, String activityType, LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByUserAndTimestampBetween(User user, LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByActivityType(String activityType);

    List<ActivityLog> findByUserOrderByTimestampDesc(User user);

}
