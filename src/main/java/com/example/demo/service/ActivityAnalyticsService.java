package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.util.AnalyticsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Service for activity-based analytics
 * Handles student activities, engagement metrics, and activity timelines
 */
@Service
public class ActivityAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityAnalyticsService.class);

    private final ActivityLogRepository activityLogRepository;
    private final ProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ContentRepository contentRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final LessonRepository lessonRepository;
    private final ExamRepository examRepository;

    public ActivityAnalyticsService(
            ActivityLogRepository activityLogRepository,
            ProgressRepository progressRepository,
            UserRepository userRepository,
            CourseRepository courseRepository,
            ContentRepository contentRepository,
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            AssignmentRepository assignmentRepository,
            LessonRepository lessonRepository,
            ExamRepository examRepository) {
        this.activityLogRepository = activityLogRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.contentRepository = contentRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
    }

    /**
     * Get course activity statistics
     */
    public Map<String, Object> getCourseActivityStats(Long courseId, String period, boolean includeTimeline) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Get course-related activities
        List<ActivityLog> activities = activityLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().isAfter(startDate) && log.getTimestamp().isBefore(endDate))
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .collect(Collectors.toList());

        // Calculate metrics
        long totalActivities = activities.size();
        long uniqueStudents = activities.stream()
                .map(log -> log.getUser().getId())
                .distinct()
                .count();

        // Activity type breakdown
        Map<String, Long> activityTypeBreakdown = activities.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.counting()
                ));

        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("totalActivities", totalActivities);
        result.put("uniqueStudents", uniqueStudents);
        result.put("activityTypeBreakdown", activityTypeBreakdown);

        if (includeTimeline) {
            List<Map<String, Object>> timeline = createDailyActivityTimeline(activities, startDate, endDate);
            result.put("timeline", timeline);
        }

        return result;
    }

    /**
     * Get advanced student analytics
     */
    public Map<String, Object> getAdvancedStudentAnalytics(Long studentId, Long courseId, String timeFilter) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, timeFilter);

        Map<String, Object> result = new HashMap<>();

        // Get all activities for the student in this course
        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate)
                .stream()
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .collect(Collectors.toList());

        // Activity summary
        Map<String, Long> activityCounts = activities.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.counting()
                ));

        // Total time spent
        long totalTimeSeconds = activities.stream()
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();

        // Most active days
        Map<String, Long> dailyActivity = activities.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getTimestamp().toLocalDate().toString(),
                        Collectors.counting()
                ));

        result.put("studentId", studentId);
        result.put("courseId", courseId);
        result.put("timeFilter", timeFilter);
        result.put("totalActivities", activities.size());
        result.put("activityCounts", activityCounts);
        result.put("totalTimeSeconds", totalTimeSeconds);
        result.put("totalTimeHours", AnalyticsUtils.secondsToHours(totalTimeSeconds));
        result.put("dailyActivity", dailyActivity);

        return result;
    }

    /**
     * Get student daily heatmap data
     */
    public Map<String, Object> getStudentDailyHeatmap(Long studentId, Long courseId, String timeFilter) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = getStartDateByFilter(timeFilter);

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate);

        if (courseId != null) {
            activities = activities.stream()
                    .filter(log -> isCourseRelatedActivity(log, courseId))
                    .collect(Collectors.toList());
        }

        // Create heatmap data: day of week + hour of day
        Map<String, Map<Integer, Long>> heatmapData = new HashMap<>();

        for (ActivityLog activity : activities) {
            String dayOfWeek = AnalyticsUtils.getDayName(activity.getTimestamp().getDayOfWeek());
            int hourOfDay = activity.getTimestamp().getHour();

            heatmapData.putIfAbsent(dayOfWeek, new HashMap<>());
            Map<Integer, Long> hourMap = heatmapData.get(dayOfWeek);
            hourMap.put(hourOfDay, hourMap.getOrDefault(hourOfDay, 0L) + 1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("courseId", courseId);
        result.put("timeFilter", timeFilter);
        result.put("heatmapData", heatmapData);
        result.put("totalActivities", activities.size());

        return result;
    }

    /**
     * Get student activity timeline (overloaded version)
     */
    public Map<String, Object> getStudentActivityTimeline(Long studentId, Long courseId, String timeFilter, int limit) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, timeFilter);

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate);

        if (courseId != null) {
            activities = activities.stream()
                    .filter(log -> isCourseRelatedActivity(log, courseId))
                    .collect(Collectors.toList());
        }

        // Limit results
        activities = activities.stream()
                .limit(limit)
                .collect(Collectors.toList());

        List<Map<String, Object>> timeline = activities.stream()
                .map(this::mapActivityToTimelineEntry)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("courseId", courseId);
        result.put("timeFilter", timeFilter);
        result.put("limit", limit);
        result.put("totalActivities", timeline.size());
        result.put("activities", timeline);

        return result;
    }

    /**
     * Get my activities (for student dashboard)
     */
    public Map<String, Object> getMyActivities(Long studentId, Long courseId, String timeFilter, int limit) {
        // Reuse the timeline method
        return getStudentActivityTimeline(studentId, courseId, timeFilter, limit);
    }

    /**
     * Get student daily activity data
     */
    public Map<String, Object> getStudentDailyActivity(Long studentId, Long courseId, int days) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = endDate.minusDays(days);

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate);

        if (courseId != null) {
            activities = activities.stream()
                    .filter(log -> isCourseRelatedActivity(log, courseId))
                    .collect(Collectors.toList());
        }

        // Group by date
        Map<LocalDate, List<ActivityLog>> activitiesByDate = activities.stream()
                .collect(Collectors.groupingBy(log -> log.getTimestamp().toLocalDate()));

        // Create daily summary
        List<Map<String, Object>> dailyData = new ArrayList<>();
        LocalDate currentDate = startDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        while (!currentDate.isAfter(end)) {
            final LocalDate date = currentDate;
            List<ActivityLog> dayActivities = activitiesByDate.getOrDefault(date, Collections.emptyList());

            long timeSeconds = dayActivities.stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum();

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("dayOfWeek", AnalyticsUtils.getDayName(date.getDayOfWeek()));
            dayData.put("activityCount", dayActivities.size());
            dayData.put("timeSeconds", timeSeconds);
            dayData.put("timeHours", AnalyticsUtils.secondsToHours(timeSeconds));

            dailyData.add(dayData);
            currentDate = currentDate.plusDays(1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("courseId", courseId);
        result.put("days", days);
        result.put("dailyData", dailyData);

        return result;
    }

    /**
     * Get student activity summary
     */
    public Map<String, Object> getStudentActivitySummary(Long studentId, Long courseId, String timeFilter) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, timeFilter);

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, endDate);

        if (courseId != null) {
            activities = activities.stream()
                    .filter(log -> isCourseRelatedActivity(log, courseId))
                    .collect(Collectors.toList());
        }

        // Calculate summary metrics
        long totalActivities = activities.size();
        long totalTimeSeconds = activities.stream()
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();

        Map<String, Long> activityTypeBreakdown = activities.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.counting()
                ));

        // Count unique days with activity
        long activeDays = activities.stream()
                .map(log -> log.getTimestamp().toLocalDate())
                .distinct()
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("courseId", courseId);
        result.put("timeFilter", timeFilter);
        result.put("totalActivities", totalActivities);
        result.put("totalTimeSeconds", totalTimeSeconds);
        result.put("totalTimeHours", AnalyticsUtils.secondsToHours(totalTimeSeconds));
        result.put("activityTypeBreakdown", activityTypeBreakdown);
        result.put("activeDays", activeDays);
        result.put("avgActivitiesPerDay", activeDays > 0 ? (double) totalActivities / activeDays : 0.0);

        return result;
    }

    /**
     * Get simple student activity timeline (original version)
     */
    public List<Map<String, Object>> getStudentActivityTimeline(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime thirtyDaysAgo = AnalyticsUtils.getIranTimeMinusDays(30);
        LocalDateTime now = AnalyticsUtils.getNowInIranTime();

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, thirtyDaysAgo, now);

        return activities.stream()
                .limit(50) // Limit to recent 50 activities
                .map(this::mapActivityToTimelineEntry)
                .collect(Collectors.toList());
    }

    // ==================== Private Helper Methods ====================

    /**
     * Get start date by time filter
     */
    private LocalDateTime getStartDateByFilter(String timeFilter) {
        LocalDateTime now = AnalyticsUtils.getNowInIranTime();

        switch (timeFilter) {
            case "7":
                return now.minusDays(7);
            case "30":
                return now.minusDays(30);
            case "90":
                return now.minusDays(90);
            case "365":
                return now.minusDays(365);
            default:
                return now.minusDays(30);
        }
    }

    /**
     * Create daily activity timeline
     */
    private List<Map<String, Object>> createDailyActivityTimeline(
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

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("dayOfWeek", AnalyticsUtils.getDayName(date.getDayOfWeek()));
            dayData.put("activityCount", dayActivities.size());

            timeline.add(dayData);
            currentDate = currentDate.plusDays(1);
        }

        return timeline;
    }

    /**
     * Map ActivityLog to timeline entry
     */
    private Map<String, Object> mapActivityToTimelineEntry(ActivityLog log) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("activityType", log.getActivityType());
        entry.put("activityTypeLabel", AnalyticsUtils.getActivityTypeLabel(log.getActivityType()));
        entry.put("timestamp", log.getTimestamp());
        entry.put("timeSpent", log.getTimeSpent());
        entry.put("timeSpentFormatted", AnalyticsUtils.formatDuration(
                log.getTimeSpent() != null ? log.getTimeSpent() : 0L));

        if (log.getMetadata() != null) {
            entry.put("metadata", log.getMetadata());
        }

        return entry;
    }

    /**
     * Get participation metrics for all students in a course
     * Shows engagement levels across lessons, exams, and assignments
     */
    public List<Map<String, Object>> getParticipationMetrics(Long courseId) {
        List<Map<String, Object>> participationMetrics = new ArrayList<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Pre-calculate total items for efficiency
        int totalLessons = course.getLessons().size();
        int totalExams = (int) course.getLessons().stream()
                .map(Lesson::getExam)
                .filter(Objects::nonNull)
                .count();
        int totalAssignments = course.getLessons().stream()
                .mapToInt(lesson -> assignmentRepository.findByLessonId(lesson.getId()).size())
                .sum();

        // For each enrolled student
        for (User student : course.getEnrolledStudents()) {
            Map<String, Object> studentData = new HashMap<>();

            studentData.put("studentId", student.getId());
            studentData.put("studentName", student.getFirstName() + " " + student.getLastName());

            // Get student's progress
            Progress progress = progressRepository.findByStudentAndCourse(student, course)
                    .orElse(null);

            int completedLessons = 0;
            if (progress != null) {
                studentData.put("viewedContent", progress.getViewedContent().size());
                completedLessons = progress.getCompletedLessons().size();
                studentData.put("completedLessons", completedLessons);
                studentData.put("lastAccessed", progress.getLastAccessed());
            } else {
                studentData.put("viewedContent", 0);
                studentData.put("completedLessons", 0);
                studentData.put("lastAccessed", null);
            }

            // Optimized: Get student's exam submissions for this course
            // Filter by course in the database query would be better, but this works
            long examsTaken = submissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .count();
            studentData.put("examsTaken", (int) examsTaken);

            // Get assignment submissions for this course
            long assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                    .count();
            studentData.put("assignmentSubmissions", (int) assignmentSubmissions);

            // Calculate overall participation
            int totalItems = totalLessons + totalExams + totalAssignments;
            int participatedItems = completedLessons + (int) examsTaken + (int) assignmentSubmissions;

            double participationRate = totalItems > 0 ?
                    (double) participatedItems / totalItems * 100 : 0;

            studentData.put("participationRate", participationRate);
            studentData.put("totalItems", totalItems);
            studentData.put("participatedItems", participatedItems);

            participationMetrics.add(studentData);
        }

        // Sort by participation rate descending
        participationMetrics.sort((p1, p2) -> {
            Double rate1 = (Double) p1.get("participationRate");
            Double rate2 = (Double) p2.get("participationRate");
            return rate2.compareTo(rate1);
        });

        return participationMetrics;
    }

    /**
     * Get comprehensive student report with activity analysis
     */
    public Map<String, Object> getStudentComprehensiveReport(Long studentId, Long courseId, int days) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> report = new HashMap<>();
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        // 1. اطلاعات پایه دانش‌آموز
        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("id", student.getId());
        studentInfo.put("name", student.getFirstName() + " " + student.getLastName());
        studentInfo.put("username", student.getUsername());
        studentInfo.put("email", student.getEmail());

        Progress progress = progressRepository.findByStudentAndCourse(student, course).orElse(null);
        if (progress != null) {
            studentInfo.put("enrollmentDate", progress.getLastAccessed());
        }
        report.put("studentInfo", studentInfo);

        // 2. آمار کلی عملکرد
        Map<String, Object> overallStats = calculateOverallStats(student, course, progress);
        report.put("overallStats", overallStats);

        // 3. فعالیت هفتگی
        List<Map<String, Object>> weeklyActivity = calculateWeeklyActivity(student, course, days);
        report.put("weeklyActivity", weeklyActivity);

        // 4. توزیع نمرات
        List<Map<String, Object>> scoreDistribution = calculateScoreDistribution(student, course);
        report.put("scoreDistribution", scoreDistribution);

        // 5. تحلیل زمان
        List<Map<String, Object>> timeAnalysis = calculateDetailedTimeAnalysis(student, course, days);
        report.put("timeAnalysis", timeAnalysis);

        // 6. فعالیت‌های اخیر
        List<Map<String, Object>> recentActivities = getStudentActivityTimelineWithDays(studentId, days);
        report.put("recentActivities", recentActivities);

        // 7. روند پیشرفت ماهانه
        List<Map<String, Object>> progressTrend = calculateProgressTrend(student, course, 6);
        report.put("progressTrend", progressTrend);

        return report;
    }

    private List<Map<String, Object>> getStudentActivityTimelineWithDays(Long studentId, int days) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, LocalDateTime.now());

        return activities.stream().map(activity -> {
            Map<String, Object> activityData = new HashMap<>();
            activityData.put("type", activity.getActivityType());
            activityData.put("timestamp", activity.getTimestamp());
            activityData.put("timeSpent", activity.getTimeSpent() != null ?
                    Math.round(activity.getTimeSpent() / 60.0) : 0.0); // Convert seconds to minutes
            activityData.put("description", generateActivityDescription(activity));

            if (activity.getMetadata() != null && !activity.getMetadata().isEmpty()) {
                activityData.put("metadata", activity.getMetadata());
            }

            if ("EXAM_SUBMISSION".equals(activity.getActivityType())) {
                Optional<Submission> submission = submissionRepository.findById(activity.getRelatedEntityId());
                submission.ifPresent(s -> activityData.put("score", s.getScore()));
            } else if ("ASSIGNMENT_SUBMISSION".equals(activity.getActivityType())) {
                Optional<AssignmentSubmission> submission = assignmentSubmissionRepository.findById(activity.getRelatedEntityId());
                submission.ifPresent(s -> activityData.put("score", s.getScore()));
            }

            return activityData;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> calculateOverallStats(User student, Course course, Progress progress) {
        Map<String, Object> stats = new HashMap<>();

        // میانگین نمرات آزمون‌ها
        List<Submission> examSubmissions = submissionRepository.findByStudent(student)
                .stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

        double averageScore = examSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);
        stats.put("averageScore", Math.round(averageScore));

        // درصد تکمیل دوره
        int totalLessons = course.getLessons().size();
        int completedLessons = progress != null ? progress.getCompletedLessons().size() : 0;
        double completionRate = totalLessons > 0 ? (double) completedLessons / totalLessons * 100 : 0;
        stats.put("completionRate", Math.round(completionRate * 10.0));

        // مجموع ساعات مطالعه
        long totalStudyseconds = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, LocalDateTime.now().minusDays(90), LocalDateTime.now())
                .stream()
                .filter(log -> log.getRelatedEntityId() != null)
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
        stats.put("totalStudyHours", Math.round(totalStudyseconds / 3600.0)); // Convert seconds to hours

        // امتیاز پایداری (بر اساس فعالیت روزانه)
        double consistencyScore = calculateConsistencyScore(student, 30);
        stats.put("consistencyScore", Math.round(consistencyScore * 10.0));

        // رتبه در کلاس
        List<Progress> allProgress = progressRepository.findAll()
                .stream()
                .filter(p -> p.getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

        if (allProgress.size() <= 1) {
            stats.put("classRank", 1);
            stats.put("totalStudents", 1);
        } else {
            long betterStudents = allProgress.stream()
                    .filter(p -> p.getCompletionPercentage() > (progress != null ? progress.getCompletionPercentage() : 0))
                    .count();
            stats.put("classRank", (int) (betterStudents + 1));
            stats.put("totalStudents", allProgress.size());
        }

        // تعداد آزمون‌های شرکت‌کرده
        stats.put("examsTaken", examSubmissions.size());

        // تعداد تکالیف ارسال‌شده
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student)
                .stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());
        stats.put("assignmentsDone", assignmentSubmissions.size());

        return stats;
    }

    private List<Map<String, Object>> calculateScoreDistribution(User student, Course course) {
        List<Submission> submissions = submissionRepository.findByStudent(student)
                .stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("0-40", 0);
        distribution.put("41-60", 0);
        distribution.put("61-80", 0);
        distribution.put("81-100", 0);

        for (Submission submission : submissions) {
            double score = submission.getScore();
            if (score <= 40) distribution.put("0-40", distribution.get("0-40") + 1);
            else if (score <= 60) distribution.put("41-60", distribution.get("41-60") + 1);
            else if (score <= 80) distribution.put("61-80", distribution.get("61-80") + 1);
            else distribution.put("81-100", distribution.get("81-100") + 1);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        String[] colors = {"#dc3545", "#fd7e14", "#ffc107", "#198754"};
        int colorIndex = 0;

        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (entry.getValue() > 0) {
                Map<String, Object> range = new HashMap<>();
                range.put("range", entry.getKey());
                range.put("count", entry.getValue());
                range.put("color", colors[colorIndex]);
                result.add(range);
            }
            colorIndex++;
        }

        return result;
    }

    private List<Map<String, Object>> calculateWeeklyActivity(User student, Course course, int days) {
        List<Map<String, Object>> weeklyData = new ArrayList<>();
        LocalDateTime endDate = LocalDateTime.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime dayStart = endDate.minusDays(i).withHour(0).withSecond(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            List<ActivityLog> dayActivities = activityLogRepository
                    .findByUserAndTimestampBetweenOrderByTimestampDesc(student, dayStart, dayEnd)
                    .stream()
                    .filter(log -> isCourseRelatedActivity(log, course.getId()))
                    .collect(Collectors.toList());

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", dayStart.toLocalDate().toString());
            dayData.put("dayName", AnalyticsUtils.getDayName(dayStart.getDayOfWeek()));
            dayData.put("views", AnalyticsUtils.countActivitiesByType(dayActivities, "CONTENT_VIEW"));
            dayData.put("submissions", AnalyticsUtils.countActivitiesByType(dayActivities, "EXAM_SUBMISSION", "ASSIGNMENT_SUBMISSION"));
            dayData.put("completions", AnalyticsUtils.countActivitiesByType(dayActivities, "LESSON_COMPLETION"));
            dayData.put("totalTime", Math.round(dayActivities.stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum() * 10.0));

            weeklyData.add(dayData);
        }

        return weeklyData;
    }

    private List<Map<String, Object>> calculateDetailedTimeAnalysis(User student, Course course, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, LocalDateTime.now())
                .stream()
                .filter(log -> isCourseRelatedActivity(log, course.getId()))
                .collect(Collectors.toList());

        Map<String, Long> timeByType = new HashMap<>();
        timeByType.put("مطالعه محتوا", 0L);
        timeByType.put("حل تکلیف", 0L);
        timeByType.put("شرکت در آزمون", 0L);
        timeByType.put("گفتگو و بحث", 0L);

        for (ActivityLog activity : activities) {
            long timeSpent = activity.getTimeSpent() != null ? activity.getTimeSpent() : 0L;

            switch (activity.getActivityType()) {
                case "CONTENT_VIEW":
                    timeByType.put("مطالعه محتوا", timeByType.get("مطالعه محتوا") + timeSpent);
                    break;
                case "ASSIGNMENT_SUBMISSION":
                    timeByType.put("حل تکلیف", timeByType.get("حل تکلیف") + timeSpent);
                    break;
                case "EXAM_SUBMISSION":
                    timeByType.put("شرکت در آزمون", timeByType.get("شرکت در آزمون") + timeSpent);
                    break;
                case "CHAT_MESSAGE_SEND":
                    timeByType.put("گفتگو و بحث", timeByType.get("گفتگو و بحث") + timeSpent);
                    break;
            }
        }

        return timeByType.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("label", entry.getKey());

                    // اصلاح: value به دقیقه تبدیل می‌شود برای نمایش، ولی seconds هم ارسال می‌شود
                    long totalSeconds = entry.getValue();
                    item.put("valueSeconds", totalSeconds); // ثانیه خام
                    item.put("valueMinutes", Math.round(totalSeconds / 60.0 * 10.0) / 10.0); // دقیقه
                    item.put("valueHours", Math.round(totalSeconds / 3600.0 * 100.0) / 100.0); // ساعت

                    // برای سازگاری با کد فعلی، value همان ثانیه باشد
                    // Frontend باید خودش تبدیل کند
                    item.put("value", totalSeconds);
                    item.put("seconds", totalSeconds);

                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> calculateProgressTrend(User student, Course course, int months) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDateTime current = LocalDateTime.now();

        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime monthStart = current.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

            List<ActivityLog> monthActivities = activityLogRepository
                    .findByUserAndTimestampBetweenOrderByTimestampDesc(student, monthStart, monthEnd)
                    .stream()
                    .filter(log -> isCourseRelatedActivity(log, course.getId()))
                    .collect(Collectors.toList());

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", AnalyticsUtils.getMonthName(monthStart.getMonthValue()));
            monthData.put("year", monthStart.getYear());
            monthData.put("lessons", AnalyticsUtils.countActivitiesByType(monthActivities, "LESSON_COMPLETION"));
            monthData.put("exams", AnalyticsUtils.countActivitiesByType(monthActivities, "EXAM_SUBMISSION"));
            monthData.put("assignments", AnalyticsUtils.countActivitiesByType(monthActivities, "ASSIGNMENT_SUBMISSION"));
            monthData.put("totalActivities", monthActivities.size());

            trend.add(monthData);
        }

        return trend;
    }

    private double calculateConsistencyScore(User student, int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<LocalDate> activeDays = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, startDate, LocalDateTime.now())
                .stream()
                .map(log -> log.getTimestamp().toLocalDate())
                .distinct()
                .collect(Collectors.toList());

        return (double) activeDays.size() / days * 100;
    }

    private String generateActivityDescription(ActivityLog activity) {
        switch (activity.getActivityType()) {
            case "LOGIN":
                return "ورود به سیستم";
            case "CONTENT_VIEW":
                return "مشاهده محتوا";
            case "LESSON_COMPLETION":
                return "تکمیل درس";
            case "EXAM_SUBMISSION":
                return "شرکت در آزمون";
            case "ASSIGNMENT_SUBMISSION":
                return "ارسال تکلیف";
            case "CHAT_MESSAGE_SEND":
                return "ارسال پیام در چت";
            case "CHAT_VIEW":
                return "مشاهده چت";
            case "FILE_ACCESS":
                return "دسترسی به فایل";
            case "LESSON_ACCESS":
                return "دسترسی به درس";
            case "EXAM_START":
                return "شروع آزمون";
            case "ASSIGNMENT_VIEW":
                return "مشاهده تکلیف";
            case "CONTENT_COMPLETION":
                return "تکمیل محتوا";
            default:
                return activity.getActivityType();
        }
    }

    // Helper methods for activity filtering
    private boolean isCourseRelatedActivity(ActivityLog log, Long courseId) {
        if (log.getRelatedEntityId() == null) {
            return false;
        }

        try {
            switch (log.getActivityType()) {
                case "CONTENT_VIEW":
                case "CONTENT_COMPLETION":
                case "FILE_ACCESS":
                    return isContentRelatedToCourse(log.getRelatedEntityId(), courseId, log.getActivityType());

                case "LESSON_COMPLETION":
                case "LESSON_ACCESS":
                    return isLessonRelatedToCourse(log.getRelatedEntityId(), courseId);

                case "EXAM_SUBMISSION":
                    return submissionRepository.findById(log.getRelatedEntityId())
                            .map(Submission::getExam)
                            .map(exam -> exam.getLesson() != null && exam.getLesson().getCourse() != null && exam.getLesson().getCourse().getId().equals(courseId))
                            .orElse(false);

                case "EXAM_START":
                    return isExamRelatedToCourse(log.getRelatedEntityId(), courseId);

                case "ASSIGNMENT_SUBMISSION":
                case "ASSIGNMENT_VIEW":
                    return isAssignmentRelatedToCourse(log.getRelatedEntityId(), courseId);

                case "CHAT_MESSAGE_SEND":
                case "CHAT_VIEW":
                    return log.getRelatedEntityId().equals(courseId);

                case "LOGIN":
                    return false;

                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAssignmentRelatedToCourse(Long relatedEntityId, Long courseId) {
        try {
            // First, check if the ID belongs to an AssignmentSubmission
            Optional<AssignmentSubmission> submissionOpt = assignmentSubmissionRepository.findById(relatedEntityId);
            if (submissionOpt.isPresent()) {
                Assignment assignment = submissionOpt.get().getAssignment();
                if (assignment != null && assignment.getLesson() != null && assignment.getLesson().getCourse() != null) {
                    return assignment.getLesson().getCourse().getId().equals(courseId);
                }
            }

            // If not a submission, check if it's an Assignment ID directly
            Optional<Assignment> assignmentOpt = assignmentRepository.findById(relatedEntityId);
            if (assignmentOpt.isPresent()) {
                Assignment assignment = assignmentOpt.get();
                if (assignment.getLesson() != null && assignment.getLesson().getCourse() != null) {
                    return assignment.getLesson().getCourse().getId().equals(courseId);
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExamRelatedToCourse(Long examId, Long courseId) {
        try {
            Optional<Exam> examOpt = examRepository.findById(examId);

            if (examOpt.isPresent()) {
                Exam exam = examOpt.get();
                return exam.getLesson() != null &&
                        exam.getLesson().getCourse() != null &&
                        exam.getLesson().getCourse().getId().equals(courseId);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLessonRelatedToCourse(Long lessonId, Long courseId) {
        try {
            Optional<Lesson> lessonOpt = lessonRepository.findById(lessonId);

            if (lessonOpt.isPresent()) {
                Lesson lesson = lessonOpt.get();
                return lesson.getCourse() != null && lesson.getCourse().getId().equals(courseId);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isContentRelatedToCourse(Long entityId, Long courseId, String activityType) {
        try {
            if ("FILE_ACCESS".equals(activityType)) {
                Optional<Content> contentOpt = contentRepository.findAll().stream()
                        .filter(content -> content.getFile() != null && content.getFile().getId().equals(entityId))
                        .findFirst();

                if (contentOpt.isPresent()) {
                    Content content = contentOpt.get();
                    return content.getLesson() != null &&
                            content.getLesson().getCourse() != null &&
                            content.getLesson().getCourse().getId().equals(courseId);
                }
                return false;
            } else {
                Optional<Content> contentOpt = contentRepository.findById(entityId);

                if (contentOpt.isPresent()) {
                    Content content = contentOpt.get();
                    return content.getLesson() != null &&
                            content.getLesson().getCourse() != null &&
                            content.getLesson().getCourse().getId().equals(courseId);
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get engagement trends for last 7 days
     */
    public List<Map<String, Object>> getEngagementTrends(User teacher) {
        List<Map<String, Object>> trends = new ArrayList<>();

        // Get last 7 days of data
        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = date.toLocalDate().atTime(23, 59, 59);

            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toLocalDate().toString());

            // Count activities for the day
            List<ActivityLog> dayActivities = activityLogRepository
                    .findByActivityTypeAndTimestampBetween("LOGIN", startOfDay, endOfDay);

            dayData.put("logins", dayActivities.size());

            // Count content views
            List<ActivityLog> contentViews = activityLogRepository
                    .findByActivityTypeAndTimestampBetween("CONTENT_VIEW", startOfDay, endOfDay);

            dayData.put("contentViews", contentViews.size());

            // Count exam submissions
            List<ActivityLog> examSubmissions = activityLogRepository
                    .findByActivityTypeAndTimestampBetween("EXAM_SUBMISSION", startOfDay, endOfDay);

            dayData.put("examSubmissions", examSubmissions.size());

            // Count assignment submissions
            List<ActivityLog> assignmentSubmissions = activityLogRepository
                    .findByActivityTypeAndTimestampBetween("ASSIGNMENT_SUBMISSION", startOfDay, endOfDay);

            dayData.put("assignmentSubmissions", assignmentSubmissions.size());

            // Calculate average session time
            double avgSessionTime = dayActivities.stream()
                    .mapToLong(ActivityLog::getTimeSpent)
                    .average()
                    .orElse(0.0);

            dayData.put("avgSessionTime", avgSessionTime);

            trends.add(dayData);
        }

        return trends;
    }
}
