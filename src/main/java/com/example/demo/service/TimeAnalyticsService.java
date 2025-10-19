package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.util.AnalyticsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for time-based analytics
 * Handles all time distribution, study time calculations, and timeline generation
 */
@Service
public class TimeAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(TimeAnalyticsService.class);

    private final ActivityLogRepository activityLogRepository;
    private final ProgressRepository progressRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public TimeAnalyticsService(
            ActivityLogRepository activityLogRepository,
            ProgressRepository progressRepository,
            CourseRepository courseRepository,
            UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.progressRepository = progressRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get time distribution for a course
     */
    public Map<String, Object> getCourseTimeDistribution(Long courseId, String period, String granularity) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Get course-related activities
        List<ActivityLog> allActivities = activityLogRepository.findAll().stream()
                .filter(log -> !log.getTimestamp().isBefore(startDate) && !log.getTimestamp().isAfter(endDate))
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .toList();

        logger.info("Total activities found for course {}: {}", courseId, allActivities.size());

        // Get enrolled students
        Set<Long> enrolledStudentIds = course.getEnrolledStudents().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Filter activities by enrolled students
        List<ActivityLog> activities = allActivities.stream()
                .filter(log -> enrolledStudentIds.contains(log.getUser().getId()))
                .collect(Collectors.toList());

        logger.info("Activities after student filtering: {}", activities.size());

        // Calculate total study time (in seconds)
        long totalTimeSeconds = activities.stream()
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();

        double totalTimeHours = AnalyticsUtils.secondsToHours(totalTimeSeconds);

        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("totalStudents", enrolledStudentIds.size());
        result.put("activeStudents", activities.stream()
                .map(log -> log.getUser().getId())
                .distinct()
                .count());
        result.put("totalTimeSeconds", totalTimeSeconds);
        result.put("totalTimeHours", totalTimeHours);
        result.put("totalActivities", activities.size());

        // Generate timeline based on granularity
        if ("daily".equalsIgnoreCase(granularity)) {
            result.put("timeline", createDailyTimelineWithStudentFilter(activities, startDate, endDate, enrolledStudentIds));
        } else if ("weekly".equalsIgnoreCase(granularity)) {
            result.put("timeline", createWeeklyTimeline(activities, startDate, endDate));
        } else {
            result.put("timeline", createDailyTimelineWithStudentFilter(activities, startDate, endDate, enrolledStudentIds));
        }

        // Activity type breakdown
        Map<String, Long> activityTypeBreakdown = activities.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.summingLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                ));

        result.put("activityTypeBreakdown", activityTypeBreakdown);

        return result;
    }

    /**
     * Get time analysis for a specific student (by activity type)
     */
    public List<Map<String, Object>> getStudentTimeAnalysis(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<ActivityLog> activities = activityLogRepository.findByUserAndTimestampBetweenOrderByTimestampDesc(
                student, AnalyticsUtils.getIranTimeMinusDays(90), AnalyticsUtils.getNowInIranTime());

        Map<String, List<ActivityLog>> groupedActivities = activities.stream()
                .collect(Collectors.groupingBy(ActivityLog::getActivityType));

        List<Map<String, Object>> timeAnalysis = new ArrayList<>();

        for (Map.Entry<String, List<ActivityLog>> entry : groupedActivities.entrySet()) {
            Map<String, Object> contentTypeData = new HashMap<>();
            String activityType = entry.getKey();
            List<ActivityLog> typeActivities = entry.getValue();

            contentTypeData.put("contentType", getContentTypeLabel(activityType));

            double avgTime = typeActivities.stream()
                    .mapToLong(ActivityLog::getTimeSpent)
                    .average()
                    .orElse(0.0);

            contentTypeData.put("avgTime", avgTime);

            long totalTime = typeActivities.stream()
                    .mapToLong(ActivityLog::getTimeSpent)
                    .sum();

            contentTypeData.put("totalTime", totalTime);

            // Calculate efficiency based on completion rate and time spent
            double efficiency = AnalyticsUtils.calculateEfficiencyScore(activityType, avgTime);
            contentTypeData.put("efficiency", efficiency);

            timeAnalysis.add(contentTypeData);
        }

        return timeAnalysis;
    }

    /**
     * Helper to get content type label
     */
    private String getContentTypeLabel(String activityType) {
        return AnalyticsUtils.getActivityTypeLabel(activityType);
    }

    /**
     * Calculate study time for a specific course and student
     */
    public long calculateCourseStudyTime(User student, Course course) {
        LocalDateTime oneMonthAgo = AnalyticsUtils.getIranTimeMinusMonths(1);
        LocalDateTime now = AnalyticsUtils.getNowInIranTime();

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, oneMonthAgo, now);

        // Filter course-related activities
        return activities.stream()
                .filter(log -> isCourseRelatedActivity(log, course.getId()))
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
    }

    /**
     * Calculate total actual study time across all courses
     */
    public long calculateActualStudyTime(User student, List<Course> courses) {
        LocalDateTime threeMonthsAgo = AnalyticsUtils.getIranTimeMinusMonths(3);
        LocalDateTime now = AnalyticsUtils.getNowInIranTime();

        List<ActivityLog> allActivities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, threeMonthsAgo, now);

        Set<Long> courseIds = courses.stream()
                .map(Course::getId)
                .collect(Collectors.toSet());

        // Filter to only include activities related to the student's courses
        return allActivities.stream()
                .filter(log -> {
                    // Check if activity is related to any of the student's courses
                    for (Long courseId : courseIds) {
                        if (isCourseRelatedActivity(log, courseId)) {
                            return true;
                        }
                    }
                    return false;
                })
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
    }

    /**
     * Create daily timeline with student filtering
     */
    public List<Map<String, Object>> createDailyTimelineWithStudentFilter(
            List<ActivityLog> activities,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Set<Long> enrolledStudentIds) {

        List<Map<String, Object>> timeline = new ArrayList<>();
        LocalDate currentDate = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        while (!currentDate.isAfter(end)) {
            final LocalDate date = currentDate;

            List<ActivityLog> dayActivities = activities.stream()
                    .filter(log -> log.getTimestamp().toLocalDate().equals(date))
                    .filter(log -> enrolledStudentIds.contains(log.getUser().getId()))
                    .collect(Collectors.toList());

            long timeSeconds = dayActivities.stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum();

            long activeStudents = dayActivities.stream()
                    .map(log -> log.getUser().getId())
                    .distinct()
                    .count();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("dayOfWeek", AnalyticsUtils.getDayName(date.getDayOfWeek()));
            dayData.put("timeSeconds", timeSeconds);
            dayData.put("timeHours", AnalyticsUtils.secondsToHours(timeSeconds));
            dayData.put("activities", dayActivities.size());
            dayData.put("activeStudents", activeStudents);

            timeline.add(dayData);
            currentDate = currentDate.plusDays(1);
        }

        return timeline;
    }

    /**
     * Create daily timeline (simple version without student filter)
     */
    public List<Map<String, Object>> createDailyTimeline(
            List<ActivityLog> activities,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        List<Map<String, Object>> timeline = new ArrayList<>();
        LocalDate currentDate = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        while (!currentDate.isAfter(end)) {
            final LocalDate date = currentDate;

            List<ActivityLog> dayActivities = activities.stream()
                    .filter(log -> log.getTimestamp().toLocalDate().equals(date))
                    .collect(Collectors.toList());

            long timeSeconds = dayActivities.stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("dayOfWeek", AnalyticsUtils.getDayName(date.getDayOfWeek()));
            dayData.put("timeSeconds", timeSeconds);
            dayData.put("timeHours", AnalyticsUtils.secondsToHours(timeSeconds));
            dayData.put("activities", dayActivities.size());

            timeline.add(dayData);
            currentDate = currentDate.plusDays(1);
        }

        return timeline;
    }

    /**
     * Create weekly timeline
     */
    public List<Map<String, Object>> createWeeklyTimeline(
            List<ActivityLog> activities,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        // Implementation similar to daily but grouped by week
        Map<Integer, List<ActivityLog>> weeklyActivities = activities.stream()
                .collect(Collectors.groupingBy(log ->
                    (int) ChronoUnit.WEEKS.between(startDate.toLocalDate(), log.getTimestamp().toLocalDate())
                ));

        return weeklyActivities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<ActivityLog> weekActivities = entry.getValue();
                    long timeSeconds = weekActivities.stream()
                            .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                            .sum();

                    Map<String, Object> weekData = new HashMap<>();
                    weekData.put("week", entry.getKey() + 1);
                    weekData.put("timeSeconds", timeSeconds);
                    weekData.put("timeHours", AnalyticsUtils.secondsToHours(timeSeconds));
                    weekData.put("activities", weekActivities.size());

                    return weekData;
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate detailed time analysis for a student in a course
     */
    public List<Map<String, Object>> calculateDetailedTimeAnalysis(User student, Course course, int days) {
        LocalDateTime startDate = AnalyticsUtils.getIranTimeMinusDays(days);
        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate)
                .stream()
                .filter(log -> isCourseRelatedActivity(log, course.getId()))
                .collect(Collectors.toList());

        // Group by activity type
        Map<String, List<ActivityLog>> groupedByType = activities.stream()
                .collect(Collectors.groupingBy(ActivityLog::getActivityType));

        return groupedByType.entrySet().stream()
                .map(entry -> {
                    String activityType = entry.getKey();
                    List<ActivityLog> typeActivities = entry.getValue();

                    long totalTime = typeActivities.stream()
                            .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                            .sum();

                    Map<String, Object> typeData = new HashMap<>();
                    typeData.put("activityType", activityType);
                    typeData.put("activityTypeLabel", AnalyticsUtils.getActivityTypeLabel(activityType));
                    typeData.put("count", typeActivities.size());
                    typeData.put("totalTimeSeconds", totalTime);
                    typeData.put("totalTimeHours", AnalyticsUtils.secondsToHours(totalTime));
                    typeData.put("avgTimeSeconds", typeActivities.isEmpty() ? 0 : totalTime / typeActivities.size());

                    return typeData;
                })
                .sorted((a, b) -> Long.compare(
                        (Long) b.get("totalTimeSeconds"),
                        (Long) a.get("totalTimeSeconds")
                ))
                .collect(Collectors.toList());
    }

    /**
     * Helper method to check if activity is related to a course
     */
    private boolean isCourseRelatedActivity(ActivityLog log, Long courseId) {
        if (log.getMetadata() == null || log.getMetadata().isEmpty()) {
            return false;
        }

        // Check for courseId in metadata
        String courseIdStr = log.getMetadata().get("courseId");
        if (courseIdStr != null) {
            try {
                return Long.parseLong(courseIdStr) == courseId;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }
}
