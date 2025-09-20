package com.example.demo.service;

import com.example.demo.model.ActivityLog;
import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.User;
import com.example.demo.repository.ActivityLogRepository;
import com.example.demo.repository.ProgressRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ActivityTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackingService.class);

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
        // Validate timeSpent for study activities
        if (isStudyActivity(activityType) && (timeSpent == null || timeSpent <= 0)) {
            logger.warn("Study activity {} logged with zero or null timeSpent ({}) for user {}",
                       activityType, timeSpent, user.getId());
        }

        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setActivityType(activityType);
        log.setRelatedEntityId(entityId);
        // Use Iran Standard Time for consistent timezone handling
        log.setTimestamp(ZonedDateTime.now(ZoneId.of("Asia/Tehran")).toLocalDateTime());
        log.setTimeSpent(timeSpent != null ? timeSpent : 0L);
        if (metadata != null) {
            log.setMetadata(metadata);
        }
        activityLogRepository.save(log);

        logger.debug("Logged activity: type={}, user={}, timeSpent={}", activityType, user.getId(), timeSpent);
    }
    
    public void updateStudyTime(User user, Course course, Long additionalTime) {
        // Update total study time for specific course only
        Progress progress = progressRepository.findByStudentAndCourse(user, course).orElse(null);
        if (progress != null) {
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

    /**
     * Check if an activity type is considered a study activity for time tracking
     */
    private boolean isStudyActivity(String activityType) {
        return Arrays.asList(
                "CONTENT_VIEW",
                "CONTENT_COMPLETION",
                "LESSON_ACCESS",
                "LESSON_COMPLETION",
                "EXAM_SUBMISSION",
                "ASSIGNMENT_SUBMISSION",
                "ASSIGNMENT_VIEW",
                "FILE_ACCESS"
        ).contains(activityType);
    }
}