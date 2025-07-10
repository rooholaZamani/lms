package com.example.demo.service;

import com.example.demo.model.ActivityLog;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.repository.ActivityLogRepository;
import com.example.demo.repository.ProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ActivityTrackingService {

    private final ActivityLogRepository activityLogRepository;
    private final ProgressRepository progressRepository;
    private final UserRepository userRepository;

    public ActivityTrackingService(ActivityLogRepository activityLogRepository,
                                   ProgressRepository progressRepository,
                                   UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
    }

    public void logActivity(User user, String activityType, Long entityId, Long timeSpent, Map<String, String> metadata) {
        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setActivityType(activityType);
        log.setRelatedEntityId(entityId);
        log.setTimestamp(LocalDateTime.now());
        log.setTimeSpent(timeSpent);
        if (metadata != null) {
            log.setMetadata(metadata);
        }
        activityLogRepository.save(log);
    }

    public void updateStudyTime(User user, Long additionalTime) {
        // Update total study time in progress records
        List<Progress> progressList = progressRepository.findByStudent(user);
        for (Progress progress : progressList) {
            Long currentTime = progress.getTotalStudyTime() != null ? progress.getTotalStudyTime() : 0L;
            progress.setTotalStudyTime(currentTime + additionalTime);
            progressRepository.save(progress);
        }
    }

    public void updateStreak(User user) {
        // Update user login streak logic
        List<Progress> progressList = progressRepository.findByStudent(user);
        LocalDateTime now = LocalDateTime.now();

        for (Progress progress : progressList) {
            LocalDateTime lastLogin = progress.getLastLoginTime();
            if (lastLogin != null) {
                long daysBetween = ChronoUnit.DAYS.between(lastLogin.toLocalDate(), now.toLocalDate());
                if (daysBetween == 1) {
                    // Consecutive day
                    progress.setCurrentStreak(progress.getCurrentStreak() != null ? progress.getCurrentStreak() + 1 : 1);
                } else if (daysBetween > 1) {
                    // Streak broken
                    progress.setCurrentStreak(1);
                }
            } else {
                progress.setCurrentStreak(1);
            }
            progress.setLastLoginTime(now);
            progressRepository.save(progress);
        }
    }
}