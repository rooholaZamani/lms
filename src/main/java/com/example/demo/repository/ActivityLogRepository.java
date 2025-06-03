package com.example.demo.repository;

import com.example.demo.model.ActivityLog;
import com.example.demo.model.User;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository {
    List<ActivityLog> findByUserAndTimestampBetweenOrderByTimestampDesc(User user, LocalDateTime start, LocalDateTime end);
    List<ActivityLog> findByActivityTypeAndTimestampBetween(String activityType, LocalDateTime start, LocalDateTime end);

}
