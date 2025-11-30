package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.util.AnalyticsUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.example.demo.model.GradeCategory;
import java.util.stream.Collectors;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final LessonRepository lessonRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ContentRepository contentRepository;
    private final AssignmentRepository assignmentRepository;
    private final ExamService examService;
    private final ProgressService progressService;
    private final TimeAnalyticsService timeAnalyticsService;
    private final ScoreAnalyticsService scoreAnalyticsService;
    private final ActivityAnalyticsService activityAnalyticsService;

    public AnalyticsService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            LessonRepository lessonRepository,
            ExamRepository examRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            ActivityLogRepository activityLogRepository,
            ContentRepository contentRepository,
            AssignmentRepository assignmentRepository,
            ExamService examService,
            ProgressService progressService,
            TimeAnalyticsService timeAnalyticsService,
            ScoreAnalyticsService scoreAnalyticsService,
            ActivityAnalyticsService activityAnalyticsService) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
        this.contentRepository = contentRepository;
        this.assignmentRepository = assignmentRepository;
        this.examService = examService;
        this.progressService = progressService;
        this.timeAnalyticsService = timeAnalyticsService;
        this.scoreAnalyticsService = scoreAnalyticsService;
        this.activityAnalyticsService = activityAnalyticsService;
    }

    /**
     * Get participation metrics for course students
     * Delegated to ActivityAnalyticsService
     */
    public List<Map<String, Object>> getParticipationMetrics(Long courseId) {
        return activityAnalyticsService.getParticipationMetrics(courseId);
    }

    /**
     * Get detailed exam analytics for a student
     * Delegated to ScoreAnalyticsService
     */
    public Map<String, Object> getExamDetails(User student, Long examId) {
        return scoreAnalyticsService.getExamDetails(student, examId);
    }

    // Delegate to ScoreAnalyticsService
    public List<Map<String, Object>> getChallengingQuestions(User teacher) {
        return scoreAnalyticsService.getChallengingQuestions(teacher);
    }

    /**
     * Get detailed analysis for a specific student in a course
     */
    public Map<String, Object> getStudentDetailedAnalysis(Long studentId, Long courseId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> analysis = new HashMap<>();

        // اطلاعات پایه
        analysis.put("courseId", courseId);
        analysis.put("courseTitle", course.getTitle());
        analysis.put("studentId", student.getId());
        analysis.put("studentName", student.getFirstName() + " " + student.getLastName());

        // Progress information
        Progress progress = progressRepository.findByStudentAndCourse(student, course).orElse(null);
        if (progress != null) {
            analysis.put("completionPercentage", progress.getCompletionPercentage());
            analysis.put("totalStudyTimeSeconds", progress.getTotalStudyTime() != null ?
                    progress.getTotalStudyTime() : 0L);
            analysis.put("streak", progress.getCurrentStreak() != null ? progress.getCurrentStreak() : 0);
            analysis.put("lastAccessed", progress.getLastAccessed());
            analysis.put("completedLessons", progress.getCompletedLessons().size());
            analysis.put("totalLessons", progress.getTotalLessons() != null ? progress.getTotalLessons() : 0);
        } else {
            analysis.put("completionPercentage", 0.0);
            analysis.put("totalStudyTimeSeconds", 0L);
            analysis.put("streak", 0);
            analysis.put("lastAccessed", null);
            analysis.put("completedLessons", 0);
            analysis.put("totalLessons", course.getLessons().size());
        }

        // Get exam submissions for this course
        List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        analysis.put("examsTaken", examSubmissions.size());
        analysis.put("averageExamScore", examSubmissions.stream()
                .mapToDouble(Submission::getScore).average().orElse(0.0));

        // Get assignment submissions
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        analysis.put("assignmentSubmissions", assignmentSubmissions.size());
        analysis.put("averageAssignmentScore", assignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average().orElse(0.0));

        // Calculate average time per lesson and exam
        long totalStudyTime = progress != null && progress.getTotalStudyTime() != null ? progress.getTotalStudyTime() : 0L;
        int completedLessons = progress != null ? progress.getCompletedLessons().size() : 0;

        // اصلاح: نام صحیح و محاسبه درست
        analysis.put("averageTimePerLessonSeconds", completedLessons > 0 ?
                Math.round((double)totalStudyTime / completedLessons) : 0);
        analysis.put("averageTimePerExamSeconds", examSubmissions.isEmpty() ? 0 :
                Math.round(examSubmissions.stream().mapToLong(s -> s.getTimeSpent() != null ? s.getTimeSpent() : 0L)
                        .average().orElse(0.0)));

        // Calculate class rank
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        if (allProgress.size() <= 1) {
            analysis.put("classRank", 1);
            analysis.put("totalStudents", 1);
        } else {
            long betterStudents = allProgress.stream()
                    .filter(p -> p.getCompletionPercentage() > (progress != null ?
                            progress.getCompletionPercentage() : 0.0))
                    .count();
            analysis.put("classRank", betterStudents + 1);
            analysis.put("totalStudents", allProgress.size());
        }

        return analysis;
    }

    /**
     * Get activity timeline for a student
     */
    public List<Map<String, Object>> getStudentActivityTimeline(Long studentId) {
        return activityAnalyticsService.getStudentActivityTimeline(studentId);
    }

    /**
     * Get exam performance for a student
     */
    public List<Map<String, Object>> getStudentExamPerformance(Long studentId) {
        return scoreAnalyticsService.getStudentExamPerformance(studentId);
    }

    /**
     * Get time analysis for a student
     */
    public List<Map<String, Object>> getStudentTimeAnalysis(Long studentId) {
        return timeAnalyticsService.getStudentTimeAnalysis(studentId);
    }

    /**
     * Get system overview for teacher
     */
    public Map<String, Object> getSystemOverview(User teacher) {
        Map<String, Object> overview = new HashMap<>();

        // Get teacher's courses
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        // Count unique students across all teacher's courses
        Set<User> uniqueStudents = new HashSet<>();
        for (Course course : teacherCourses) {
            uniqueStudents.addAll(course.getEnrolledStudents());
        }
        int totalStudents = uniqueStudents.size();

        // Calculate average completion across all courses
        List<Progress> allProgress = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<Progress> courseProgress = progressRepository.findAll().stream()
                    .filter(p -> p.getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allProgress.addAll(courseProgress);
        }

        // Calculate modern activity-based average completion
        double averageCompletion = 0.0;
        if (!uniqueStudents.isEmpty()) {
            double totalCompletion = 0.0;
            int validProgressCount = 0;

            for (User student : uniqueStudents) {
                for (Course course : teacherCourses) {
                    if (course.getEnrolledStudents().contains(student)) {
                        double studentCourseProgress = calculateProgressFromActivities(student, course);
                        totalCompletion += studentCourseProgress;
                        validProgressCount++;
                    }
                }
            }

            if (validProgressCount > 0) {
                averageCompletion = totalCompletion / validProgressCount;
            }
        }

        // Get all exam submissions for teacher's courses
        List<Submission> allSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<Submission> courseSubmissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allSubmissions.addAll(courseSubmissions);
        }

        double averageScore = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        // Get all assignment submissions for teacher's courses
        List<AssignmentSubmission> allAssignmentSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<AssignmentSubmission> courseAssignments = assignmentSubmissionRepository.findAll().stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allAssignmentSubmissions.addAll(courseAssignments);
        }

        // Calculate proper average study time per student using activity-based calculation
        double avgTimePerStudent = 0.0;
        if (!uniqueStudents.isEmpty()) {
            avgTimePerStudent = uniqueStudents.stream()
                    .mapToLong(student -> calculateActualStudyTime(student, teacherCourses))
                    .average()
                    .orElse(0.0);
        }

        // Calculate total study hours for overview
        long totalHours = uniqueStudents.stream()
                .mapToLong(student -> calculateActualStudyTime(student, teacherCourses))
                .sum();

        overview.put("totalStudents", totalStudents);
        overview.put("totalCourses", teacherCourses.size());
        overview.put("averageCompletion", averageCompletion);
        overview.put("averageScore", averageScore);
        overview.put("totalHours", totalHours);
        overview.put("avgTimePerStudent", avgTimePerStudent);
        overview.put("totalExams", allSubmissions.size());
        overview.put("totalAssignments", allAssignmentSubmissions.size());

        // Calculate engagement and retention rates
        overview.put("engagementRate", calculateEngagementRate(allProgress));
        overview.put("retentionRate", calculateRetentionRate(allProgress));

        // Add trends (compare with previous period)
        overview.put("trends", calculateTrends(teacher));

        return overview;
    }

    /**
     * Get time analysis across content types
     */
    public List<Map<String, Object>> getTimeAnalysis(User teacher) {
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
        List<Map<String, Object>> timeAnalysis = new ArrayList<>();

        // Group activities by content type
        Map<String, List<ActivityLog>> contentTypeActivities = new HashMap<>();

        for (Course course : teacherCourses) {
            List<User> students = course.getEnrolledStudents();

            for (User student : students) {
                List<ActivityLog> activities = activityLogRepository
                        .findByUserAndTimestampBetweenOrderByTimestampDesc(
                                student, LocalDateTime.now().minusDays(30), LocalDateTime.now());

                for (ActivityLog activity : activities) {
                    String contentType = getContentTypeLabel(activity.getActivityType());
                    contentTypeActivities.computeIfAbsent(contentType, k -> new ArrayList<>()).add(activity);
                }
            }
        }

        for (Map.Entry<String, List<ActivityLog>> entry : contentTypeActivities.entrySet()) {
            Map<String, Object> contentData = new HashMap<>();
            String contentType = entry.getKey();
            List<ActivityLog> activities = entry.getValue();

            contentData.put("contentType", contentType);

            double avgTime = activities.stream()
                    .mapToLong(ActivityLog::getTimeSpent)
                    .average()
                    .orElse(0.0);

            contentData.put("avgTime", avgTime);
            contentData.put("difficulty", AnalyticsUtils.getDifficultyLabel(avgTime));
            contentData.put("studentCount", activities.size());

            // Calculate completion rate and engagement (placeholder values)
            contentData.put("completionRate", 85.0); // Placeholder: 85% completion rate

            // Calculate engagement based on average time spent
            double avgTimeSpent = activities.isEmpty() ? 0.0 :
                activities.stream().mapToLong(ActivityLog::getTimeSpent).average().orElse(0.0);
            contentData.put("engagement", Math.min(95.0, 50 + (avgTimeSpent / 10)));

            timeAnalysis.add(contentData);
        }

        return timeAnalysis;
    }

    /**
     * Get question difficulty analysis
     */
    public List<Map<String, Object>> getQuestionDifficultyAnalysis(User teacher) {
        List<Question> teacherQuestions = questionRepository.findByTeacher(teacher);

        // Group questions by topic (you might need to add topic field to Question entity)
        Map<String, List<Question>> questionsByTopic = teacherQuestions.stream()
                .collect(Collectors.groupingBy(q -> "General Topic")); // Placeholder grouping

        List<Map<String, Object>> difficultyAnalysis = new ArrayList<>();

        for (Map.Entry<String, List<Question>> entry : questionsByTopic.entrySet()) {
            Map<String, Object> topicData = new HashMap<>();
            String topic = entry.getKey();
            List<Question> questions = entry.getValue();

            topicData.put("topic", topic);

            // Optimized: Single pass to count questions by difficulty instead of 3 separate streams
            int easyQuestions = 0;
            int mediumQuestions = 0;
            int hardQuestions = 0;

            for (Question q : questions) {
                if (q.getDifficulty() != null) {
                    if (q.getDifficulty() <= 2.0) {
                        easyQuestions++;
                    } else if (q.getDifficulty() <= 4.0) {
                        mediumQuestions++;
                    } else {
                        hardQuestions++;
                    }
                }
            }

            topicData.put("easyQuestions", easyQuestions);
            topicData.put("mediumQuestions", mediumQuestions);
            topicData.put("hardQuestions", hardQuestions);

            // Calculate average score and time for questions in this topic (placeholders)
            double avgScore = 75.0; // Placeholder
            double avgTime = 210.0; // Placeholder: 3.5 minutes in seconds
            double difficultyRating = 3.2; // Placeholder: on scale of 1-5

            topicData.put("avgScore", avgScore);
            topicData.put("avgTime", avgTime);
            topicData.put("difficultyRating", difficultyRating);

            difficultyAnalysis.add(topicData);
        }

        return difficultyAnalysis;
    }

    /**
     * Get lesson performance analysis grouped by course
     */
    public Map<String, Object> getLessonPerformanceAnalysis(User teacher) {
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
        Map<String, Object> result = new HashMap<>();
        Map<String, List<Map<String, Object>>> lessonsByCourse = new HashMap<>();
        List<Map<String, Object>> allLessons = new ArrayList<>();

        for (Course course : teacherCourses) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            List<Map<String, Object>> courseLessons = new ArrayList<>();

            for (Lesson lesson : lessons) {
                Map<String, Object> lessonData = new HashMap<>();
                lessonData.put("lesson", lesson.getTitle());
                lessonData.put("courseId", course.getId());
                lessonData.put("courseTitle", course.getTitle());

                // Calculate average time spent on lesson
                List<ActivityLog> lessonActivities = activityLogRepository
                        .findByActivityTypeAndTimestampBetween("LESSON_COMPLETION",
                                LocalDateTime.now().minusDays(90), LocalDateTime.now())
                        .stream()
                        .filter(a -> a.getRelatedEntityId().equals(lesson.getId()))
                        .collect(Collectors.toList());

                double avgTime = lessonActivities.stream()
                        .mapToLong(ActivityLog::getTimeSpent)
                        .average()
                        .orElse(0.0);

                lessonData.put("avgTime", avgTime);

                // Calculate completion rate
                List<Progress> courseProgress = progressRepository.findAll().stream()
                        .filter(p -> p.getCourse().getId().equals(course.getId()))
                        .collect(Collectors.toList());

                long completedCount = courseProgress.stream()
                        .filter(p -> p.getCompletedLessons().contains(lesson.getId()))
                        .count();

                double completionRate = courseProgress.isEmpty() ? 0 :
                        (double) completedCount / courseProgress.size() * 100;

                lessonData.put("completionRate", completionRate);

                // Calculate average score if lesson has exam
                double avgScore = 0.0;
                if (lesson.getExam() != null) {
                    List<Submission> examSubmissions = submissionRepository.findByExam(lesson.getExam());
                    avgScore = examSubmissions.stream()
                            .mapToDouble(Submission::getScore)
                            .average()
                            .orElse(0.0);
                }

                lessonData.put("avgScore", avgScore);
                lessonData.put("difficulty", AnalyticsUtils.getDifficultyLabel(avgTime));
                lessonData.put("studentFeedback", 4.5); // Placeholder - you'd need feedback system

                courseLessons.add(lessonData);
                allLessons.add(lessonData);
            }

            lessonsByCourse.put(course.getTitle(), courseLessons);
        }

        result.put("lessonsByCourse", lessonsByCourse);
        result.put("allLessons", allLessons);

        return result;
    }

    /**
     * Get engagement trends
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



    private List<Map<String, Object>> getRecentActivity(User student) {
        List<Map<String, Object>> recentActivities = new ArrayList<>();

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, sevenDaysAgo, LocalDateTime.now());

        for (ActivityLog activity : activities.stream().limit(10).collect(Collectors.toList())) {
            Map<String, Object> activityData = new HashMap<>();
            activityData.put("type", activity.getActivityType());
            activityData.put("timestamp", activity.getTimestamp());
            activityData.put("description", generateActivityDescription(activity));
            if (activity.getMetadata() != null && !activity.getMetadata().isEmpty()) {
                activityData.put("metadata", activity.getMetadata());
            }
            recentActivities.add(activityData);
        }

        return recentActivities;
    }

    private double calculateEngagementRate(List<Progress> progressList) {
        if (progressList.isEmpty()) return 0.0;

        long activeStudents = progressList.stream()
                .filter(p -> p.getLastAccessed() != null &&
                        p.getLastAccessed().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        return (double) activeStudents / progressList.size() * 100;
    }

    private double calculateRetentionRate(List<Progress> progressList) {
        if (progressList.isEmpty()) return 0.0;

        long retainedStudents = progressList.stream()
                .filter(p -> p.getCompletionPercentage() > 10) // Students who completed at least 10%
                .count();

        return (double) retainedStudents / progressList.size() * 100;
    }

    private Map<String, Object> calculateTrends(User teacher) {
        Map<String, Object> trends = new HashMap<>();

        // Compare current month with previous month
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1);
        LocalDateTime startOfPrevMonth = startOfMonth.minusMonths(1);

        // Get teacher's courses
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        // Calculate current month students
        int currentStudents = teacherCourses.stream()
                .mapToInt(course -> course.getEnrolledStudents().size())
                .sum();

        // For trends, we'll use placeholder values since we don't have historical data
        trends.put("studentsTrend", 12); // +12% growth
        trends.put("coursesTrend", 8);   // +8% growth
        trends.put("completionTrend", 5); // +5% improvement
        trends.put("scoreTrend", 3);     // +3% improvement

        return trends;
    }

    // Additional required methods for complete functionality

    public Map<String, Object> getStudentPerformanceForTeacher(User teacher, Long studentId, Long courseId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Map<String, Object> performance = new HashMap<>();

        // Verify student is in teacher's courses
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
        List<Course> studentCourses = teacherCourses.stream()
                .filter(course -> course.getEnrolledStudents().contains(student))
                .collect(Collectors.toList());

        if (studentCourses.isEmpty()) {
            throw new RuntimeException("Student is not enrolled in any of your courses");
        }

        // If specific course is requested, filter to that course
        if (courseId != null) {
            studentCourses = studentCourses.stream()
                    .filter(course -> course.getId().equals(courseId))
                    .collect(Collectors.toList());

            if (studentCourses.isEmpty()) {
                throw new RuntimeException("Student is not enrolled in the specified course");
            }
        }

        // Basic student info
        performance.put("studentId", student.getId());
        performance.put("studentName", student.getFirstName() + " " + student.getLastName());
        performance.put("username", student.getUsername());

        // Progress analysis
        List<Course> finalStudentCourses = studentCourses;
        List<Progress> studentProgress = progressRepository.findByStudent(student).stream()
                .filter(p -> finalStudentCourses.stream().anyMatch(c -> c.getId().equals(p.getCourse().getId())))
                .collect(Collectors.toList());

        double averageCompletion = studentProgress.stream()
                .mapToDouble(Progress::getCompletionPercentage)
                .average()
                .orElse(0.0);

        // Calculate study time from ActivityLog
        long totalStudyTimeFromActivities = calculateActualStudyTime(student, studentCourses);

        performance.put("enrolledCourses", studentCourses.size());
        performance.put("averageCompletion", Math.round(averageCompletion));
        performance.put("totalStudyTime", totalStudyTimeFromActivities); // Return time in seconds for frontend to display correctly
        performance.put("averageStudyTimePerCourse", studentCourses.isEmpty() ? 0 :
                totalStudyTimeFromActivities / studentCourses.size()); // Return average time in seconds

        // Exam performance
        List<Course> finalStudentCourses1 = studentCourses;
        List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                .filter(s -> finalStudentCourses1.stream().anyMatch(c ->
                        c.getId().equals(s.getExam().getLesson().getCourse().getId())))
                .collect(Collectors.toList());

        double averageExamScore = examSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        long passedExams = examSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        performance.put("examsTaken", examSubmissions.size());
        performance.put("averageExamScore", Math.round(averageExamScore));
        performance.put("examPassRate", examSubmissions.isEmpty() ? 0 :
                Math.round((double) passedExams / examSubmissions.size() * 100));

        // Assignment performance
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                .filter(as -> finalStudentCourses1.stream().anyMatch(c ->
                        c.getId().equals(as.getAssignment().getLesson().getCourse().getId())))
                .collect(Collectors.toList());

        double averageAssignmentScore = assignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        performance.put("assignmentSubmissions", assignmentSubmissions.size());
        performance.put("averageAssignmentScore", Math.round(averageAssignmentScore * 10.0));

        // Recent activity
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        List<ActivityLog> recentActivities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, oneWeekAgo, LocalDateTime.now());

        performance.put("recentActivityCount", recentActivities.size());
        performance.put("lastAccessed", studentProgress.stream()
                .map(Progress::getLastAccessed)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));

        // Course-specific details
        List<Map<String, Object>> courseDetails = new ArrayList<>();
        for (Course course : studentCourses) {
            Map<String, Object> courseData = new HashMap<>();
            courseData.put("courseId", course.getId());
            courseData.put("courseName", course.getTitle());

            Progress courseProgress = studentProgress.stream()
                    .filter(p -> p.getCourse().getId().equals(course.getId()))
                    .findFirst()
                    .orElse(null);

            if (courseProgress != null) {
                courseData.put("completion", courseProgress.getCompletionPercentage());
                courseData.put("lastAccessed", courseProgress.getLastAccessed());
            } else {
                courseData.put("completion", 0.0);
                courseData.put("lastAccessed", null);
            }

            // Calculate study time for each course from ActivityLog
            long courseStudyTime = calculateCourseStudyTime(student, course);
            courseData.put("studyTimeMinutes", Math.round(courseStudyTime / 60.0)); // Convert seconds to minutes for display

            courseDetails.add(courseData);
        }

        performance.put("courseDetails", courseDetails);

        return performance;
    }

    public Map<String, Object> getStudentsProgressOverview(User teacher) {
        Map<String, Object> overview = new HashMap<>();

        // Get all teacher's courses
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        // Collect all students and their progress
        List<Progress> allStudentProgress = new ArrayList<>();
        Set<User> allStudents = new HashSet<>();

        for (Course course : teacherCourses) {
            // Get enrolled students for this course
            List<User> enrolledStudents = course.getEnrolledStudents();
            allStudents.addAll(enrolledStudents);

            // Get progress records only for enrolled students
            for (User student : enrolledStudents) {
                Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
                progressOpt.ifPresent(allStudentProgress::add);
            }
        }

        // Calculate statistics
        int totalStudents = allStudents.size();
        int activeStudents = (int) allStudentProgress.stream()
                .filter(p -> p.getLastAccessed() != null &&
                        p.getLastAccessed().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        // Calculate modern activity-based average completion
        double averageCompletion = 0.0;
        if (!allStudents.isEmpty()) {
            double totalCompletion = 0.0;
            int validProgressCount = 0;

            for (User student : allStudents) {
                for (Course course : teacherCourses) {
                    if (course.getEnrolledStudents().contains(student)) {
                        double studentCourseProgress = calculateProgressFromActivities(student, course);
                        totalCompletion += studentCourseProgress;
                        validProgressCount++;
                    }
                }
            }

            if (validProgressCount > 0) {
                averageCompletion = totalCompletion / validProgressCount;
            }
        }

        long completedStudents = allStudentProgress.stream()
                .filter(p -> p.getCompletionPercentage() >= 100)
                .count();

        // Get exam statistics for enrolled students only
        List<Submission> allSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<User> enrolledStudents = course.getEnrolledStudents();
            for (User student : enrolledStudents) {
                List<Submission> studentSubmissions = submissionRepository.findByStudent(student).stream()
                        .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                        .collect(Collectors.toList());
                allSubmissions.addAll(studentSubmissions);
            }
        }

        double averageExamScore = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        long passedExams = allSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        // Get assignment statistics for enrolled students only
        List<AssignmentSubmission> allAssignmentSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<User> enrolledStudents = course.getEnrolledStudents();
            for (User student : enrolledStudents) {
                List<AssignmentSubmission> studentAssignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                        .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
                        .collect(Collectors.toList());
                allAssignmentSubmissions.addAll(studentAssignmentSubmissions);
            }
        }

        // Build overview
        overview.put("totalStudents", totalStudents);
        overview.put("activeStudents", activeStudents);
        overview.put("inactiveStudents", totalStudents - activeStudents);
        overview.put("averageCompletion", Math.round(averageCompletion));
        overview.put("completedStudents", completedStudents);
        overview.put("totalCourses", teacherCourses.size());
        overview.put("totalExamsTaken", allSubmissions.size());
        overview.put("averageExamScore", Math.round(averageExamScore));
        overview.put("examPassRate", allSubmissions.isEmpty() ? 0 :
                Math.round((double) passedExams / allSubmissions.size() * 100));
        overview.put("totalAssignmentSubmissions", allAssignmentSubmissions.size());

        // Activity levels
        long highPerformers = allStudentProgress.stream()
                .filter(p -> p.getCompletionPercentage() >= 80)
                .count();
        long struggling = allStudentProgress.stream()
                .filter(p -> p.getCompletionPercentage() < 30)
                .count();

        overview.put("highPerformers", highPerformers);
        overview.put("strugglingStudents", struggling);

        return overview;
    }

    public List<Map<String, Object>> getCourseStudentsSummary(User teacher, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Verify teacher owns the course
        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only view your own courses");
        }

        List<Map<String, Object>> studentsSummary = new ArrayList<>();

        for (User student : course.getEnrolledStudents()) {
            Map<String, Object> studentData = new HashMap<>();

            studentData.put("studentId", student.getId());
            studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
            studentData.put("username", student.getUsername());

            // Progress
            Progress progress = progressRepository.findByStudentAndCourse(student, course)
                    .orElse(null);

            if (progress != null) {
                studentData.put("completion", progress.getCompletionPercentage());
                studentData.put("studyTime", progress.getTotalStudyTime());
                studentData.put("lastAccessed", progress.getLastAccessed());
                studentData.put("completedLessons", progress.getCompletedLessons().size());
            } else {
                studentData.put("completion", 0.0);
                studentData.put("studyTime", 0L);
                studentData.put("lastAccessed", null);
                studentData.put("completedLessons", 0);
            }

            // Exam performance
            List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            double averageScore = examSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            studentData.put("examsTaken", examSubmissions.size());
            studentData.put("averageScore", Math.round(averageScore));

            // Assignment performance
            List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("assignmentSubmissions", assignmentSubmissions.size());

            studentsSummary.add(studentData);
        }

        // Sort by completion percentage (highest first)
        studentsSummary.sort((s1, s2) -> {
            Double completion1 = (Double) s1.get("completion");
            Double completion2 = (Double) s2.get("completion");
            return completion2.compareTo(completion1);
        });

        return studentsSummary;
    }



    @Transactional
    public void recalculateStudyTimes() {
        List<Progress> allProgress = progressRepository.findAll();

        for (Progress progress : allProgress) {
            User student = progress.getStudent();
            Course course = progress.getCourse();

            // محاسبه زمان واقعی از ActivityLog
            long actualStudyTime = calculateCourseStudyTime(student, course);

            // به‌روزرسانی Progress
            progress.setTotalStudyTime(actualStudyTime);
            progressRepository.save(progress);
        }
    }

    /**
     * Get comprehensive student report with activity analysis
     * Delegated to ActivityAnalyticsService
     */
    public Map<String, Object> getStudentComprehensiveReport(Long studentId, Long courseId, int days) {
        return activityAnalyticsService.getStudentComprehensiveReport(studentId, courseId, days);
    }

    /**
     * Get course time distribution for students
     */
    public Map<String, Object> getCourseTimeDistribution(Long courseId, String period, String granularity) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // دریافت activity logs مربوط به این course (using inclusive date boundaries)
        List<ActivityLog> allActivities = activityLogRepository.findAll().stream()
                .filter(log -> !log.getTimestamp().isBefore(startDate) && !log.getTimestamp().isAfter(endDate))
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .toList();

        logger.info("Total activities found after time and course filtering: {}", allActivities.size());

        // DEBUG: Log activity details to investigate timeSpent values
        logger.info("=== DEBUG: Activity details ===");
        allActivities.forEach(activity -> {
            logger.info("Activity ID: {}, Type: {}, User: {}, TimeSpent: {}, Timestamp: {}",
                activity.getId(), activity.getActivityType(), activity.getUser().getId(),
                activity.getTimeSpent(), activity.getTimestamp());
        });

        // Get enrolled students for validation
        Set<Long> enrolledStudentIds = course.getEnrolledStudents().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> "ROLE_STUDENT".equals(role.getName()))) // Only include STUDENT role
                .map(User::getId)
                .collect(Collectors.toSet());

        logger.info("Enrolled students with STUDENT role: {}", enrolledStudentIds.size());
        logger.info("Enrolled student IDs: {}", enrolledStudentIds);

        // Filter activities to only include enrolled students with STUDENT role AND study activities
        List<ActivityLog> studentActivities = allActivities.stream()
                .filter(log -> {
                    User user = log.getUser();
                    boolean isStudent = user.getRoles().stream()
                            .anyMatch(role -> "ROLE_STUDENT".equals(role.getName()));
                    boolean isEnrolled = enrolledStudentIds.contains(user.getId());
                    boolean isStudyActivity = isStudyActivity(log.getActivityType());

                    if (!isStudent) {
                        String roleNames = user.getRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.joining(", "));
                        logger.debug("Excluding activity from user {} with roles: {}", user.getId(), roleNames);
                    }
                    if (!isEnrolled && isStudent) {
                        logger.debug("Excluding activity from non-enrolled student: {}", user.getId());
                    }
                    if (!isStudyActivity && isStudent && isEnrolled) {
                        logger.debug("Excluding non-study activity: {} from student: {}", log.getActivityType(), user.getId());
                    }

                    return isStudent && isEnrolled && isStudyActivity;
                })
                .collect(Collectors.toList());

        logger.info("Activities after student filtering: {}", studentActivities.size());

        // Log activity breakdown by user
        Map<Long, List<ActivityLog>> activitiesByUser = studentActivities.stream()
                .collect(Collectors.groupingBy(log -> log.getUser().getId()));

        logger.info("Activity breakdown by user:");
        activitiesByUser.forEach((userId, userActivities) -> {
            logger.info("  User {}: {} activities", userId, userActivities.size());
        });

        // Group by student
        Map<Long, List<ActivityLog>> activitiesByStudent = studentActivities.stream()
                .collect(Collectors.groupingBy(log -> log.getUser().getId()));

        // DEBUG: Log Progress table data for comparison
        logger.info("=== DEBUG: Progress table data ===");
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());
        allProgress.forEach(progress -> {
            logger.info("Progress: Student {}, Course {}, TotalStudyTime: {} seconds",
                progress.getStudent().getId(), progress.getCourse().getId(), progress.getTotalStudyTime());
        });

        // محاسبه total time per student from ActivityLog
        Map<Long, Long> timePerStudent = new HashMap<>();
        for (Map.Entry<Long, List<ActivityLog>> entry : activitiesByStudent.entrySet()) {
            Long totalTime = entry.getValue().stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum();
            timePerStudent.put(entry.getKey(), totalTime);
        }


        List<Long> times = new ArrayList<>(timePerStudent.values());

        // Time distribution ranges
        List<Map<String, Object>> ranges = Arrays.asList(
                createTimeRange("فعالیت کم (< 1 ساعت)", 0L, 3600L, times),
                createTimeRange("فعالیت متوسط (1-3 ساعت)", 3600L, 10800L, times),
                createTimeRange("فعالیت زیاد (3-5 ساعت)", 10800L, 18000L, times),
                createTimeRange("فعالیت بسیار زیاد (> 5 ساعت)", 18000L, null, times)
        );

        // Timeline data - pass filtered student activities and fallback time data
        List<Map<String, Object>> timeline = new ArrayList<>();
        if ("daily".equals(granularity)) {
            timeline = createDailyTimelineWithStudentFilter(studentActivities, startDate, endDate, enrolledStudentIds);
        } else if ("weekly".equals(granularity)) {
            timeline = createWeeklyTimeline(studentActivities, startDate, endDate);
        }

        // Calculate averages
        long totalStudents = course.getEnrolledStudents().size();
        double averageTimePerStudent = times.isEmpty() ? 0 : times.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        logger.info("Final results: totalStudents={}, activeStudentsInActivities={}",
                   totalStudents, activitiesByStudent.size());

        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("totalStudents", totalStudents);
        result.put("averageTimePerStudent", Math.round(averageTimePerStudent));
        result.put("timeDistribution", Map.of("ranges", ranges));
        result.put("timeline", timeline);

        return result;
    }

    /**
     * Get course activity statistics
     */
    public Map<String, Object> getCourseActivityStats(Long courseId, String period, boolean includeTimeline) {
        return activityAnalyticsService.getCourseActivityStats(courseId, period, includeTimeline);
    }

    /**
     * Get course exam scores with filtering and aggregation
     */
    public Map<String, Object> getCourseExamScores(Long courseId, String period, Long examId, boolean includeDetails) {
        return scoreAnalyticsService.getCourseExamScores(courseId, period, examId, includeDetails);
    }

    /**
     * Get lesson progress analysis for a course
     */
    public Map<String, Object> getCourseLessonProgress(Long courseId, String period) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        // Get all lessons for this course
        List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);

        // Get all progress records for this course
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        long totalStudents = course.getEnrolledStudents().size();

        // Calculate lesson progress
        List<Map<String, Object>> lessonProgressList = new ArrayList<>();

        for (Lesson lesson : lessons) {
            Map<String, Object> lessonData = new HashMap<>();

            // Count students who completed this lesson
            long completedStudents = allProgress.stream()
                    .filter(p -> p.getCompletedLessons().contains(lesson.getId()))
                    .count();

            double completionRate = totalStudents > 0 ?
                    (double) completedStudents / totalStudents * 100 : 0;

            // Calculate average time spent on this lesson
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

            List<ActivityLog> lessonActivities = activityLogRepository.findAll().stream()
                    .filter(log -> "LESSON_COMPLETION".equals(log.getActivityType()) ||
                            "LESSON_ACCESS".equals(log.getActivityType()))
                    .filter(log -> log.getRelatedEntityId().equals(lesson.getId()))
                    .filter(log -> log.getTimestamp().isAfter(startDate) &&
                            log.getTimestamp().isBefore(endDate))
                    .collect(Collectors.toList());

            double averageTime = lessonActivities.stream()
                    .mapToLong(ActivityLog::getTimeSpent)
                    .average()
                    .orElse(0.0);

            lessonData.put("lessonId", lesson.getId());
            lessonData.put("lessonTitle", lesson.getTitle());
            lessonData.put("completionRate", Math.round(completionRate * 10.0));
            lessonData.put("completedStudents", completedStudents);
            lessonData.put("totalStudents", totalStudents);
            lessonData.put("averageTime", Math.round(averageTime));

            lessonProgressList.add(lessonData);
        }

        // Calculate distribution based on overall course progress
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("excellent", 0);
        distribution.put("good", 0);
        distribution.put("average", 0);
        distribution.put("poor", 0);

        for (Progress progress : allProgress) {
            double completionPercentage = progress.getCompletionPercentage();

            if (completionPercentage >= 80) {
                distribution.merge("excellent", 1, Integer::sum);
            } else if (completionPercentage >= 60) {
                distribution.merge("good", 1, Integer::sum);
            } else if (completionPercentage >= 40) {
                distribution.merge("average", 1, Integer::sum);
            } else {
                distribution.merge("poor", 1, Integer::sum);
            }
        }

        result.put("lessons", lessonProgressList);
        result.put("distribution", distribution);

        return result;
    }

    // Helper methods for new analytics functionality

    /**
     * Calculate grade distribution using percentage-based categories.
     * Uses default assignment max score of 20.
     *
     * @param scores List of raw scores
     * @return Map with category names and counts
     */
    private Map<String, Integer> calculateGradeDistribution(List<Integer> scores) {
        return calculateGradeDistribution(scores, 20.0); // Default assignment max score
    }

    /**
     * Calculate grade distribution using percentage-based categories with custom max score.
     *
     * @param scores List of raw scores
     * @param maxScore Maximum possible score for the assessment
     * @return Map with category names and counts including percentage ranges
     */
    private Map<String, Integer> calculateGradeDistribution(List<Integer> scores, double maxScore) {
        Map<String, Integer> distribution = new HashMap<>();

        // Initialize all categories with 0 count to ensure they appear in results
        for (GradeCategory category : GradeCategory.values()) {
            distribution.put(category.name().toLowerCase(), 0);
        }

        for (Integer score : scores) {
            if (score != null) {
                GradeCategory category = GradeCategory.fromScore(score, maxScore);
                distribution.merge(category.name().toLowerCase(), 1, Integer::sum);
            }
        }

        return distribution;
    }

    /**
     * Calculate enhanced grade distribution with additional metadata.
     *
     * @param scores List of raw scores
     * @param maxScore Maximum possible score
     * @return Map with categories, counts, percentages, and display info
     */
    private Map<String, Object> calculateEnhancedGradeDistribution(List<Integer> scores, double maxScore) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> distribution = calculateGradeDistribution(scores, maxScore);

        int totalScores = scores.size();
        List<Map<String, Object>> categories = new ArrayList<>();

        for (GradeCategory category : GradeCategory.values()) {
            String categoryName = category.name().toLowerCase();
            int count = distribution.get(categoryName);
            double percentage = totalScores > 0 ? (double) count / totalScores * 100 : 0;

            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put("name", categoryName);
            categoryData.put("label", category.getEnglishLabel());
            categoryData.put("persianLabel", category.getPersianLabel());
            categoryData.put("count", count);
            categoryData.put("percentage", Math.round(percentage * 100.0) / 100.0);
            categoryData.put("range", category.getRange());
            categoryData.put("color", category.getColor());

            categories.add(categoryData);
        }

        result.put("categories", categories);
        result.put("totalCount", totalScores);
        result.put("distribution", distribution); // Keep backward compatibility

        return result;
    }

    /**
     * Calculate grade distribution for exam submissions using each exam's totalPossibleScore.
     *
     * @param examSubmissions List of exam submissions
     * @return Grade distribution map
     */
    private Map<String, Integer> calculateExamGradeDistribution(List<Submission> examSubmissions) {
        Map<String, Integer> distribution = new HashMap<>();

        // Initialize all categories with 0 count
        for (GradeCategory category : GradeCategory.values()) {
            distribution.put(category.name().toLowerCase(), 0);
        }

        for (Submission submission : examSubmissions) {
            if (submission.getScore() != null && submission.getExam() != null) {
                Integer totalPossibleScore = submission.getExam().getTotalPossibleScore();
                if (totalPossibleScore != null && totalPossibleScore > 0) {
                    GradeCategory category = GradeCategory.fromScore(submission.getScore(), totalPossibleScore);
                    distribution.merge(category.name().toLowerCase(), 1, Integer::sum);
                } else {
                    // Fallback to default scoring if totalPossibleScore is not set
                    GradeCategory category = GradeCategory.fromScore(submission.getScore(), 20.0);
                    distribution.merge(category.name().toLowerCase(), 1, Integer::sum);
                }
            }
        }

        return distribution;
    }

    /**
     * Calculate grade distribution for assignment submissions using default max score of 20.
     *
     * @param assignmentSubmissions List of assignment submissions
     * @return Grade distribution map
     */
    private Map<String, Integer> calculateAssignmentGradeDistribution(List<AssignmentSubmission> assignmentSubmissions) {
        Map<String, Integer> distribution = new HashMap<>();

        // Initialize all categories with 0 count
        for (GradeCategory category : GradeCategory.values()) {
            distribution.put(category.name().toLowerCase(), 0);
        }

        for (AssignmentSubmission submission : assignmentSubmissions) {
            if (submission.getScore() != null) {
                // Use default assignment max score of 20
                GradeCategory category = GradeCategory.fromScore(submission.getScore(), 20.0);
                distribution.merge(category.name().toLowerCase(), 1, Integer::sum);
            }
        }

        return distribution;
    }

    /**
     * Get enhanced analytics with submission details for assignments.
     * This addresses the issue where only aggregated distribution is shown,
     * missing individual assignment submission details.
     *
     * @param studentId Student ID
     * @param courseId Course ID (optional)
     * @param timeFilter Time filter
     * @return Enhanced analytics with individual submission tracking
     */
    public Map<String, Object> getEnhancedStudentGradesDistribution(Long studentId, Long courseId, String timeFilter) {
        return scoreAnalyticsService.getEnhancedStudentGradesDistribution(studentId, courseId, timeFilter);
    }

    private Map<String, Object> calculateEnhancedExamGradeDistribution(List<Submission> examSubmissions) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> distribution = calculateExamGradeDistribution(examSubmissions);

        List<Map<String, Object>> examDetails = examSubmissions.stream()
                .filter(s -> s.getScore() != null)
                .map(s -> {
                    Integer totalScore = s.getExam().getTotalPossibleScore();
                    double percentage = totalScore != null && totalScore > 0
                            ? GradeCategory.calculatePercentage(s.getScore(), totalScore)
                            : GradeCategory.calculatePercentage(s.getScore(), 20.0);

                    GradeCategory category = totalScore != null && totalScore > 0
                            ? GradeCategory.fromScore(s.getScore(), totalScore)
                            : GradeCategory.fromScore(s.getScore(), 20.0);

                    Map<String, Object> examDetail = new HashMap<>();
                    examDetail.put("examTitle", s.getExam().getTitle());
                    examDetail.put("score", s.getScore());
                    examDetail.put("totalPossible", totalScore != null ? totalScore : 20);
                    examDetail.put("percentage", Math.round(percentage * 100.0) / 100.0);
                    examDetail.put("category", category.name().toLowerCase());
                    examDetail.put("categoryLabel", category.getPersianLabel());
                    examDetail.put("date", s.getSubmissionTime());
                    return examDetail;
                })
                .collect(Collectors.toList());

        result.put("distribution", distribution);
        result.put("examDetails", examDetails);
        result.put("totalCount", examSubmissions.size());

        return result;
    }

    private Map<String, Object> calculateEnhancedAssignmentGradeDistribution(List<AssignmentSubmission> assignmentSubmissions) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> distribution = calculateAssignmentGradeDistribution(assignmentSubmissions);

        // Detailed assignment breakdown - this will help debug the "only 1 showing" issue
        List<Map<String, Object>> allAssignmentDetails = assignmentSubmissions.stream()
                .map(as -> {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("assignmentTitle", as.getAssignment().getTitle());
                    detail.put("submittedAt", as.getSubmittedAt());
                    detail.put("isGraded", as.getScore() != null);

                    if (as.getScore() != null) {
                        double percentage = GradeCategory.calculatePercentage(as.getScore(), 20.0);
                        GradeCategory category = GradeCategory.fromScore(as.getScore(), 20.0);

                        detail.put("score", as.getScore());
                        detail.put("totalPossible", 20);
                        detail.put("percentage", Math.round(percentage * 100.0) / 100.0);
                        detail.put("category", category.name().toLowerCase());
                        detail.put("categoryLabel", category.getPersianLabel());
                        detail.put("gradedAt", as.getGradedAt());
                    } else {
                        detail.put("status", "pending_grading");
                    }

                    return detail;
                })
                .collect(Collectors.toList());

        // Separate graded vs ungraded for better visibility
        List<Map<String, Object>> gradedAssignments = allAssignmentDetails.stream()
                .filter(detail -> (Boolean) detail.get("isGraded"))
                .collect(Collectors.toList());

        List<Map<String, Object>> pendingAssignments = allAssignmentDetails.stream()
                .filter(detail -> !(Boolean) detail.get("isGraded"))
                .collect(Collectors.toList());

        result.put("distribution", distribution);
        result.put("allAssignments", allAssignmentDetails);
        result.put("gradedAssignments", gradedAssignments);
        result.put("pendingAssignments", pendingAssignments);
        result.put("totalSubmitted", assignmentSubmissions.size());
        result.put("totalGraded", gradedAssignments.size());
        result.put("totalPending", pendingAssignments.size());

        return result;
    }

    private Map<String, Object> createTimeRange(String label, Long minTime, Long maxTime, List<Long> times) {
        int count = 0;
        for (Long time : times) {
            if (minTime != null && time < minTime) continue;
            if (maxTime != null && time >= maxTime) continue;
            count++;
        }

        double percentage = times.isEmpty() ? 0 : (double) count / times.size() * 100;

        Map<String, Object> range = new HashMap<>();
        range.put("label", label);
        range.put("minTime", minTime);
        range.put("maxTime", maxTime);
        range.put("studentCount", count);
        range.put("percentage", Math.round(percentage));

        return range;
    }

    private List<Map<String, Object>> createDailyTimeline(List<ActivityLog> activities, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Map<String, Object>> timelineMap = new HashMap<>();
        Map<String, Set<Long>> uniqueStudentsPerDay = new HashMap<>();

        LocalDateTime current = startDate.toLocalDate().atStartOfDay();
        while (!current.isAfter(endDate)) {
            String dateStr = current.toLocalDate().toString();
            timelineMap.put(dateStr, new HashMap<>());
            timelineMap.get(dateStr).put("date", dateStr);
            timelineMap.get(dateStr).put("totalseconds", 0L);
            timelineMap.get(dateStr).put("activeStudents", 0);
            uniqueStudentsPerDay.put(dateStr, new HashSet<>());
            current = current.plusDays(1);
        }

        // Group activities by date and track unique students
        for (ActivityLog activity : activities) {
            String dateStr = activity.getTimestamp().toLocalDate().toString();
            if (timelineMap.containsKey(dateStr)) {
                Map<String, Object> dayData = timelineMap.get(dateStr);
                Long currentseconds = (Long) dayData.get("totalseconds");
                dayData.put("totalseconds", currentseconds + activity.getTimeSpent());

                // Track unique students per day
                uniqueStudentsPerDay.get(dateStr).add(activity.getUser().getId());
            }
        }

        // Update active students count with actual unique student count
        for (String dateStr : timelineMap.keySet()) {
            Map<String, Object> dayData = timelineMap.get(dateStr);
            dayData.put("activeStudents", uniqueStudentsPerDay.get(dateStr).size());
        }

        return new ArrayList<>(timelineMap.values());
    }

    /**
     * Enhanced daily timeline creation with proper student filtering and debugging
     */
    private List<Map<String, Object>> createDailyTimelineWithStudentFilter(List<ActivityLog> activities, LocalDateTime startDate, LocalDateTime endDate, Set<Long> enrolledStudentIds) {

        logger.info("=== Creating daily timeline with student filter ===");
        logger.info("Input activities: {}", activities.size());
        logger.info("Valid student IDs: {}", enrolledStudentIds);

        Map<String, Map<String, Object>> timelineMap = new HashMap<>();
        Map<String, Set<Long>> uniqueStudentsPerDay = new HashMap<>();

        LocalDateTime current = startDate.toLocalDate().atStartOfDay();
        while (!current.isAfter(endDate)) {
            String dateStr = current.toLocalDate().toString();
            timelineMap.put(dateStr, new HashMap<>());
            timelineMap.get(dateStr).put("date", dateStr);
            timelineMap.get(dateStr).put("totalseconds", 0L);
            timelineMap.get(dateStr).put("activeStudents", 0);
            uniqueStudentsPerDay.put(dateStr, new HashSet<>());
            current = current.plusDays(1);
        }

        // Group activities by date and track unique students with additional validation
        for (ActivityLog activity : activities) {
            String dateStr = activity.getTimestamp().toLocalDate().toString();
            Long userId = activity.getUser().getId();

            if (timelineMap.containsKey(dateStr)) {
                // Double-check that this user is an enrolled student (should already be filtered)
                if (!enrolledStudentIds.contains(userId)) {
                    logger.warn("Activity from non-enrolled user {} found in filtered activities, skipping", userId);
                    continue;
                }

                // Double-check user role
                boolean hasStudentRole = activity.getUser().getRoles().stream()
                        .anyMatch(role -> "ROLE_STUDENT".equals(role.getName()));
                if (!hasStudentRole) {
                    String roleNames = activity.getUser().getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.joining(", "));
                    logger.warn("Activity from non-student user {} (roles: {}) found in filtered activities, skipping",
                               userId, roleNames);
                    continue;
                }

                Map<String, Object> dayData = timelineMap.get(dateStr);
                Long currentseconds = (Long) dayData.get("totalseconds");
                Long activityTimeSpent = activity.getTimeSpent() != null ? activity.getTimeSpent() : 0L;
                dayData.put("totalseconds", currentseconds + activityTimeSpent);

                // Track unique students per day
                uniqueStudentsPerDay.get(dateStr).add(userId);

                logger.info("DEBUG Timeline: Added activity for student {} on {}, activity timeSpent: {}, new total: {} seconds",
                           userId, dateStr, activityTimeSpent, currentseconds + activityTimeSpent);
            }
        }

        // FALLBACK: For students with no ActivityLog but have Progress.totalStudyTime, add to most recent day
        Set<Long> studentsWithActivityLog = activities.stream()
                .map(log -> log.getUser().getId())
                .collect(Collectors.toSet());

        String mostRecentDate = endDate.toLocalDate().toString();

        // Update active students count with actual unique student count and log details
        for (String dateStr : timelineMap.keySet()) {
            Map<String, Object> dayData = timelineMap.get(dateStr);
            Set<Long> studentsOnDay = uniqueStudentsPerDay.get(dateStr);
            int activeStudentsCount = studentsOnDay.size();

            dayData.put("activeStudents", activeStudentsCount);

            if (activeStudentsCount > 0) {
                logger.info("Date {}: {} active students: {}, total seconds: {}",
                    dateStr, activeStudentsCount, studentsOnDay, dayData.get("totalseconds"));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(timelineMap.values());
        logger.info("=== Timeline creation complete, returning {} days of data ===", result.size());

        return result;
    }

    private List<Map<String, Object>> createWeeklyTimeline(List<ActivityLog> activities, LocalDateTime startDate, LocalDateTime endDate) {
        // Similar implementation for weekly timeline
        return new ArrayList<>();
    }

    private Map<String, Object> createContentStudyMetrics(List<ActivityLog> contentActivities, long totalStudents, Long courseId) {
        Set<Long> uniqueStudents = contentActivities.stream()
                .map(log -> log.getUser().getId())
                .collect(Collectors.toSet());

        double participationRate = totalStudents > 0 ? (double) uniqueStudents.size() / totalStudents * 100 : 0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("participationRate", Math.round(participationRate));
        metrics.put("totalViews", contentActivities.size());
        metrics.put("averageViewsPerStudent", totalStudents > 0 ? (double) contentActivities.size() / totalStudents : 0);
        metrics.put("completionRate", 78); // Placeholder - نیاز به محاسبه دقیق‌تر

        return metrics;
    }

    private Map<String, Object> createChatActivityMetrics(List<ActivityLog> chatActivities, long totalStudents) {
        Set<Long> uniqueStudents = chatActivities.stream()
                .map(log -> log.getUser().getId())
                .collect(Collectors.toSet());

        double participationRate = totalStudents > 0 ? (double) uniqueStudents.size() / totalStudents * 100 : 0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("participationRate", Math.round(participationRate));
        metrics.put("totalMessages", chatActivities.size());
        metrics.put("averageMessagesPerStudent", totalStudents > 0 ? (double) chatActivities.size() / totalStudents : 0);
        metrics.put("activeParticipants", uniqueStudents.size());

        return metrics;
    }

    private Map<String, Object> createAssignmentMetrics(Long courseId, LocalDateTime startDate, LocalDateTime endDate, long totalStudents) {
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findAll().stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .filter(as -> as.getSubmittedAt().isAfter(startDate) && as.getSubmittedAt().isBefore(endDate))
                .collect(Collectors.toList());

        Set<Long> uniqueStudents = assignmentSubmissions.stream()
                .map(as -> as.getStudent().getId())
                .collect(Collectors.toSet());

        double participationRate = totalStudents > 0 ? (double) uniqueStudents.size() / totalStudents * 100 : 0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("participationRate", Math.round(participationRate));
        metrics.put("totalSubmissions", assignmentSubmissions.size());
        metrics.put("averageSubmissionsPerStudent", totalStudents > 0 ? (double) assignmentSubmissions.size() / totalStudents : 0);
        metrics.put("completionRate", Math.round(participationRate));

        return metrics;
    }

    private Map<String, Object> createExamMetrics(Long courseId, LocalDateTime startDate, LocalDateTime endDate, long totalStudents) {
        List<Submission> examSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .filter(s -> s.getSubmissionTime().isAfter(startDate) && s.getSubmissionTime().isBefore(endDate))
                .collect(Collectors.toList());

        Set<Long> uniqueStudents = examSubmissions.stream()
                .map(s -> s.getStudent().getId())
                .collect(Collectors.toSet());

        double participationRate = totalStudents > 0 ? (double) uniqueStudents.size() / totalStudents * 100 : 0;
        double completionRate = totalStudents > 0 ? (double) examSubmissions.size() / totalStudents * 100 : 0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("participationRate", Math.round(participationRate));
        metrics.put("totalAttempts", examSubmissions.size());
        metrics.put("averageAttemptsPerStudent", totalStudents > 0 ? (double) examSubmissions.size() / totalStudents : 0);
        metrics.put("completionRate", Math.round(completionRate));

        return metrics;
    }

    private List<Map<String, Object>> createEngagementTrend(List<ActivityLog> activities, LocalDateTime startDate, LocalDateTime endDate) {
        // Group by week and activity type
        List<Map<String, Object>> trend = new ArrayList<>();

        // Simplified weekly grouping
        LocalDateTime current = startDate;
        while (current.isBefore(endDate)) {
            LocalDateTime weekEnd = current.plusWeeks(1);

            LocalDateTime finalCurrent = current;
            List<ActivityLog> weekActivities = activities.stream()
                    .filter(log -> log.getTimestamp().isAfter(finalCurrent) && log.getTimestamp().isBefore(weekEnd))
                    .collect(Collectors.toList());

            Map<String, Object> weekData = new HashMap<>();
            weekData.put("week", "2025-W" + current.getDayOfYear() / 7);
            weekData.put("contentViews", weekActivities.stream()
                    .filter(log -> "CONTENT_VIEW".equals(log.getActivityType()))
                    .count());
            weekData.put("chatMessages", weekActivities.stream()
                    .filter(log -> "CHAT_MESSAGE_SEND".equals(log.getActivityType()))
                    .count());
            weekData.put("assignments", weekActivities.stream()
                    .filter(log -> "ASSIGNMENT_SUBMISSION".equals(log.getActivityType()))
                    .count());
            weekData.put("examAttempts", weekActivities.stream()
                    .filter(log -> "EXAM_SUBMISSION".equals(log.getActivityType()))
                    .count());

            trend.add(weekData);
            current = weekEnd;
        }

        return trend;
    }

    /**
     * Get difficult lessons based on completion rates and scores (updated for assignments)
     */
    // Delegate to ScoreAnalyticsService
    public List<Map<String, Object>> getDifficultLessons(Long courseId) {
        return scoreAnalyticsService.getDifficultLessons(courseId);
    }

    /**
     * Identify struggling students based on progress and scores (updated for assignments)
     */
    // Delegate to ScoreAnalyticsService
    public List<Map<String, Object>> getStrugglingStudents(Long courseId) {
        return scoreAnalyticsService.getStrugglingStudents(courseId);
    }


    /**
     * Get overall student performance across all courses (updated to use assignments)
     */
    public Map<String, Object> getStudentPerformance(User student) {
        Map<String, Object> performance = new HashMap<>();

        // Get all progress records for the student
        List<Progress> progressList = progressRepository.findByStudent(student);

        // Get all exam submissions
        List<Submission> examSubmissions = submissionRepository.findByStudent(student);

        // Get all assignment submissions (instead of exercise submissions)
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student);

        // Calculate average completion rate
        double averageCompletion = progressList.stream()
                .mapToDouble(p -> (double) p.getCompletedLessonCount() / p.getTotalLessons() * 100)
                .average()
                .orElse(0.0);

        // Calculate average exam score
        double averageExamScore = examSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        // Calculate average assignment score (instead of exercise score)
        double averageAssignmentScore = assignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        // Count completed courses
        long completedCourses = progressList.stream()
                .filter(p -> p.getCompletionPercentage() >= 100)
                .count();

        // Count passed exams
        long passedExams = examSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        // Count graded assignments (instead of exercise metrics)
        long gradedAssignments = assignmentSubmissions.stream()
                .filter(AssignmentSubmission::isGraded)
                .count();

        // Prepare response
        performance.put("totalCourses", progressList.size());
        performance.put("completedCourses", completedCourses);
        performance.put("averageCompletion", averageCompletion);
        performance.put("examsTaken", examSubmissions.size());
        performance.put("passedExams", passedExams);
        performance.put("averageExamScore", averageExamScore);
        performance.put("assignmentsSubmitted", assignmentSubmissions.size()); // Changed from exercisesTaken
        performance.put("averageAssignmentScore", averageAssignmentScore); // Changed from averageExerciseScore
        performance.put("gradedAssignments", gradedAssignments);

        // Add time-based metrics like recent activity
        performance.put("recentActivity", getRecentActivity(student));

        return performance;
    }


    /**
     * Compare student performance against course average (updated to use assignments)
     */
    public Map<String, Object> getStudentComparison(User student, Long courseId) {
        Map<String, Object> comparison = new HashMap<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Get student's progress in the course
        Progress studentProgress = progressRepository.findByStudentAndCourse(student, course)
                .orElseThrow(() -> new RuntimeException("Progress not found"));

        // Get all students' progress in the course
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Calculate class average completion
        double averageCompletion = allProgress.stream()
                .mapToDouble(p -> (double) p.getCompletedLessonCount() / p.getTotalLessons() * 100)
                .average()
                .orElse(0.0);

        // Get student's exam submissions for this course
        List<Submission> studentSubmissions = submissionRepository.findByStudent(student).stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Calculate student's average exam score
        double studentExamAverage = studentSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        // Get all students' exam submissions for this course
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Calculate class average exam score
        double classExamAverage = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        // Get student's assignment submissions for this course (instead of exercise)
        List<AssignmentSubmission> studentAssignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        double studentAssignmentAverage = studentAssignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        // Get all students' assignment submissions for this course
        List<AssignmentSubmission> allAssignmentSubmissions = assignmentSubmissionRepository.findAll().stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        double classAssignmentAverage = allAssignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        // Build comparison data
        comparison.put("studentCompletion", studentProgress.getCompletionPercentage());
        comparison.put("classAverageCompletion", averageCompletion);
        double studentCompletionRate = (double) studentProgress.getCompletedLessonCount() / studentProgress.getTotalLessons() * 100;
        comparison.put("completionPercentile", AnalyticsUtils.calculatePercentile(studentCompletionRate,
                allProgress.stream().mapToDouble(p -> (double) p.getCompletedLessonCount() / p.getTotalLessons() * 100).toArray()));

        comparison.put("studentExamAverage", studentExamAverage);
        comparison.put("classExamAverage", classExamAverage);
        comparison.put("examPercentile", AnalyticsUtils.calculatePercentile(studentExamAverage,
                allSubmissions.stream().mapToDouble(Submission::getScore).toArray()));

        // Assignment comparison (instead of exercise)
        comparison.put("studentAssignmentAverage", studentAssignmentAverage);
        comparison.put("classAssignmentAverage", classAssignmentAverage);
        comparison.put("assignmentSubmissions", studentAssignmentSubmissions.size());
        comparison.put("assignmentPercentile", AnalyticsUtils.calculatePercentile(studentAssignmentAverage,
                allAssignmentSubmissions.stream()
                        .filter(as -> as.getScore() != null)
                        .mapToDouble(as -> as.getScore().doubleValue())
                        .toArray()));

        return comparison;
    }

    /**
     * Get overall course performance metrics for teacher (updated to include assignments)
     */
    public Map<String, Object> getCoursePerformanceForTeacher(Long courseId) {
        Map<String, Object> performance = new HashMap<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Calculate overall course metrics using modern activity-based calculation
        double averageCompletion = course.getEnrolledStudents().stream()
                .mapToDouble(student -> calculateProgressFromActivities(student, course))
                .average()
                .orElse(0.0);

        long completedStudents = allProgress.stream()
                .filter(p -> p.getCompletionPercentage() >= 100)
                .count();

        // Get all exam submissions for this course
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        double averageExamScore = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        long passedExams = allSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        // Get all assignment submissions for this course (instead of exercise submissions)
        List<AssignmentSubmission> allAssignmentSubmissions = assignmentSubmissionRepository.findAll().stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        double averageAssignmentScore = allAssignmentSubmissions.stream()
                .filter(as -> as.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .average()
                .orElse(0.0);

        long gradedAssignments = allAssignmentSubmissions.stream()
                .filter(AssignmentSubmission::isGraded)
                .count();

        // Calculate average study time for all students in the course
        double averageTimeSpent = allProgress.stream()
                .mapToLong(p -> p.getTotalStudyTime() != null ? p.getTotalStudyTime() : 0L)
                .average()
                .orElse(0.0);

        // seconds for frontend display
        averageTimeSpent = averageTimeSpent ;

        // Calculate completion rate (percentage of students who completed the course)
        double completionRate = course.getEnrolledStudents().isEmpty() ? 0 :
                (double) completedStudents / course.getEnrolledStudents().size() * 100;

        // Build performance data with frontend-expected structure
        performance.put("totalStudents", course.getEnrolledStudents().size());
        performance.put("studentCount", course.getEnrolledStudents().size()); // Alternative key for compatibility
        performance.put("activeStudents", allProgress.size());
        performance.put("completedStudents", completedStudents);
        performance.put("averageProgress", Math.round(averageCompletion * 100.0) / 100.0); // Frontend expects this key
        performance.put("averageCompletion", averageCompletion); // Keep for backward compatibility
        performance.put("averageTimeSpent", Math.round(averageTimeSpent * 100.0) / 100.0); // In minutes
        performance.put("completionRate", Math.round(completionRate * 100.0) / 100.0);

        // Exam metrics
        performance.put("examsTaken", allSubmissions.size());
        performance.put("passedExams", passedExams);
        performance.put("averageExamScore", Math.round(averageExamScore * 100.0) / 100.0);
        performance.put("passingRate", allSubmissions.isEmpty() ? 0 :
                Math.round((double) passedExams / allSubmissions.size() * 100 * 100.0) / 100.0); // Frontend expects this key
        performance.put("passRate", allSubmissions.isEmpty() ? 0 : (double) passedExams / allSubmissions.size() * 100);

        // Assignment metrics (instead of exercise metrics)
        performance.put("assignmentSubmissions", allAssignmentSubmissions.size());
        performance.put("averageAssignmentScore", Math.round(averageAssignmentScore * 100.0) / 100.0);
        performance.put("gradedAssignments", gradedAssignments);
        performance.put("assignmentGradingRate", allAssignmentSubmissions.isEmpty() ? 0 :
                Math.round((double) gradedAssignments / allAssignmentSubmissions.size() * 100 * 100.0) / 100.0);

        return performance;
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
            case "ASSIGNMENT_SUBMISSION": // Changed from EXERCISE_SUBMISSION
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
            case "ASSIGNMENT_VIEW": // New activity type for viewing assignments
                return "مشاهده تکلیف";

            case "CONTENT_COMPLETION": // New activity type for viewing assignments
                return "تکمیل محتوا";
            default:
                return activity.getActivityType();
        }
    }
    private String getContentTypeLabel(String activityType) {
        switch (activityType) {
            case "CONTENT_VIEW":
                return "مشاهده محتوا";
            case "ASSIGNMENT_SUBMISSION": // Changed from EXERCISE_SUBMISSION
                return "تکالیف";
            case "EXAM_SUBMISSION":
                return "آزمون‌ها";
            case "LESSON_COMPLETION":
                return "درس‌ها";
            case "CHAT_MESSAGE_SEND":
                return "ارسال پیام چت";
            case "CHAT_VIEW":
                return "مشاهده چت";
            case "FILE_ACCESS":
                return "فایل‌ها";
            case "ASSIGNMENT_VIEW":
                return "مشاهده تکالیف";
            default:
                return "سایر";
        }
    }


    private boolean isStudyActivity(String activityType) {
        return Arrays.asList(
                "CONTENT_VIEW",
                "CONTENT_COMPLETION",
                "LESSON_ACCESS",
                "LESSON_COMPLETION",
                "EXAM_SUBMISSION",
                "ASSIGNMENT_SUBMISSION", // Changed from EXERCISE_SUBMISSION
                "ASSIGNMENT_VIEW", // Added assignment viewing
                "FILE_ACCESS"
        ).contains(activityType);
    }

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

                case "ASSIGNMENT_SUBMISSION": // Changed from EXERCISE_SUBMISSION
                case "ASSIGNMENT_VIEW": // Added assignment viewing
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


    /**
     * Get daily engagement statistics (updated for assignments)
     */
    public Map<String, Object> getDailyEngagementStats(User teacher) {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime now = LocalDateTime.now();

        // Calculate averages over the last 30 days
        List<ActivityLog> loginActivities = activityLogRepository
                .findByActivityTypeAndTimestampBetween("LOGIN", thirtyDaysAgo, now);

        List<ActivityLog> contentViews = activityLogRepository
                .findByActivityTypeAndTimestampBetween("CONTENT_VIEW", thirtyDaysAgo, now);

        List<ActivityLog> examSubmissions = activityLogRepository
                .findByActivityTypeAndTimestampBetween("EXAM_SUBMISSION", thirtyDaysAgo, now);

        List<ActivityLog> assignmentSubmissions = activityLogRepository
                .findByActivityTypeAndTimestampBetween("ASSIGNMENT_SUBMISSION", thirtyDaysAgo, now);

        stats.put("avgDailyLogins", loginActivities.size() / 30);
        stats.put("loginTrend", AnalyticsUtils.calculateTrend(loginActivities, 30));

        stats.put("avgContentViews", contentViews.size() / 30);
        stats.put("viewTrend", AnalyticsUtils.calculateTrend(contentViews, 30));

        stats.put("avgExamSubmissions", examSubmissions.size() / 30);
        stats.put("examTrend", AnalyticsUtils.calculateTrend(examSubmissions, 30));

        stats.put("avgAssignmentSubmissions", assignmentSubmissions.size() / 30); // Changed from exercise
        stats.put("assignmentTrend", AnalyticsUtils.calculateTrend(assignmentSubmissions, 30)); // Changed from exercise

        return stats;
    }































    /**
     * Calculate study time for a specific course
     */
    public long calculateCourseStudyTime(User student, Course course) {
        return timeAnalyticsService.calculateCourseStudyTime(student, course);
    }

    /**
     * Calculate actual study time across multiple courses
     */
    public long calculateActualStudyTime(User student, List<Course> courses) {
        return timeAnalyticsService.calculateActualStudyTime(student, courses);
    }

    /**
     * Check if activity is related to any of the given courses
     */
    private boolean isCourseRelatedActivityForCourses(ActivityLog log, List<Course> courses) {
        for (Course course : courses) {
            if (isCourseRelatedActivity(log, course.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if assignment is related to course
     */
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
            // Log the exception for debugging purposes
            // logger.error("Error checking if assignment is related to course", e);
            return false;
        }
    }

    /**
     * Helper method to check if exam is related to course
     */
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

    /**
     * Helper method to check if lesson is related to course
     */
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

    /**
     * Helper method to check if content is related to course
     */
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
     * Get top performers for a course in different categories
     */
    // Delegate to ScoreAnalyticsService
    public Map<String, List<Map<String, Object>>> getTopPerformers(Long courseId) {
        return scoreAnalyticsService.getTopPerformers(courseId);
    }

    /**
     * Get challenging questions for a specific course
     */

    public Map<String, Object> getChallengingQuestionsForCourse(Long courseId, String period) {
        Map<String, Object> result = new HashMap<>();

        // Validate course exists
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Calculate time range
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Get all questions for this course
        List<Question> courseQuestions = questionRepository.findByCourseId(courseId);

        List<Map<String, Object>> challengingQuestions = new ArrayList<>();

        // Statistics for summary
        int totalDifficult = 0;
        double totalDifficultyScore = 0.0;
        int needsReview = 0;
        double maxDifficulty = 0.0;

        for (Question question : courseQuestions) {
            // Get submissions for this question within time period
            List<Submission> submissions = submissionRepository
                    .findByExamAndSubmissionTimeBetween(question.getExam(), startDate, endDate);

            if (submissions.isEmpty()) continue;

            // Calculate error rate
            long totalAnswers = submissions.size();
            long incorrectAnswers = submissions.stream()
                    .mapToLong(sub -> {
                        try {
                            // استفاده از ExamService برای ارزیابی پاسخ
                            Map<String, Object> studentAnswers = parseSubmissionAnswers(sub.getAnswersJson());
                            Object studentAnswer = studentAnswers.get(question.getId().toString());

                            if (studentAnswer == null) {
                                return 1; // No answer = incorrect
                            }

                            // استفاده از ExamService برای ارزیابی صحیح پاسخ
                            Object evaluation = examService.evaluateAnswerWithPartialScoring(question, studentAnswer);

                            if (evaluation instanceof Boolean) {
                                return ((Boolean) evaluation) ? 0 : 1;
                            } else if (evaluation instanceof Number) {
                                // اگر نمره جزئی است، بیش از 50% را درست در نظر بگیر
                                double score = ((Number) evaluation).doubleValue();
                                double maxScore = question.getPoints() != null ? question.getPoints() : 1.0;
                                return (score / maxScore) >= 0.5 ? 0 : 1;
                            }

                            return 1; // در صورت عدم قطعیت، اشتباه در نظر بگیر

                        } catch (Exception e) {
                            System.err.println("Error evaluating answer for question " + question.getId() +
                                    " in submission " + sub.getId() + ": " + e.getMessage());
                            return 1; // در صورت خطا، اشتباه در نظر بگیر
                        }
                    })
                    .sum();

            double errorRate = totalAnswers > 0 ? (double) incorrectAnswers / totalAnswers * 100 : 0;

            // Only include questions with high error rate (> 60%)
            if (errorRate > 60) {
                Map<String, Object> questionData = new HashMap<>();
                questionData.put("questionId", question.getId());
                questionData.put("questionText", question.getText());
                questionData.put("errorRate", Math.round(errorRate * 100.0) / 100.0);
                questionData.put("difficulty", Math.round((errorRate / 100.0 * 5) * 100.0) / 100.0); // Scale 0-5
                questionData.put("totalAttempts", totalAnswers);
                questionData.put("incorrectCount", incorrectAnswers);
                questionData.put("topic", question.getExam() != null ? question.getExam().getTitle() : "General");

                // Add lesson and exam info
                if (question.getExam() != null && question.getExam().getLesson() != null) {
                    questionData.put("lessonTitle", question.getExam().getLesson().getTitle());
                    questionData.put("examTitle", question.getExam().getTitle());
                }

                challengingQuestions.add(questionData);

                // Update statistics
                totalDifficult++;
                totalDifficultyScore += errorRate;
                if (errorRate > 80) needsReview++;
                if (errorRate > maxDifficulty) maxDifficulty = errorRate;
            }
        }

        // Sort by error rate (descending)
        challengingQuestions.sort((q1, q2) -> {
            Double rate1 = (Double) q1.get("errorRate");
            Double rate2 = (Double) q2.get("errorRate");
            return rate2.compareTo(rate1);
        });

        // Limit to top 20 most challenging
        if (challengingQuestions.size() > 20) {
            challengingQuestions = challengingQuestions.subList(0, 20);
        }

        // Build summary statistics
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalDifficultQuestions", totalDifficult);
        summary.put("averageDifficulty", totalDifficult > 0 ? Math.round(totalDifficultyScore / totalDifficult * 100.0) / 100.0 : 0);
        summary.put("questionsNeedingReview", needsReview);
        summary.put("maxDifficulty", Math.round(maxDifficulty * 100.0) / 100.0);
        summary.put("period", period);

        result.put("challengingQuestions", challengingQuestions);
        result.put("summary", summary);
        result.put("course", Map.of(
                "id", course.getId(),
                "title", course.getTitle()
        ));

        return result;
    }

    /**
     * Get at-risk students for a specific course using weighted scoring system
     */
    // Delegate to ScoreAnalyticsService
    public Map<String, Object> getAtRiskStudents(Long courseId, String period) {
        return scoreAnalyticsService.getAtRiskStudents(courseId, period);
    }

    /**
     * Get trend analysis for a specific course
     */
    public Map<String, Object> getTrendAnalysis(Long courseId, String period) {
        Map<String, Object> result = new HashMap<>();

        // Validate course exists
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Calculate time range
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Determine data points based on period
        int dataPoints;
        ChronoUnit intervalUnit;

        switch (period.toLowerCase()) {
            case "week":
                dataPoints = 7;
                intervalUnit = ChronoUnit.DAYS;
                break;
            case "quarter":
                dataPoints = 12;
                intervalUnit = ChronoUnit.WEEKS;
                break;
            case "semester":
                dataPoints = 24;
                intervalUnit = ChronoUnit.WEEKS;
                break;
            case "month":
            default:
                dataPoints = 30;
                intervalUnit = ChronoUnit.DAYS;
                break;
        }

        List<Map<String, Object>> trends = new ArrayList<>();

        for (int i = dataPoints - 1; i >= 0; i--) {
            LocalDateTime periodEnd = endDate.minus(i, intervalUnit);
            LocalDateTime periodStart = periodEnd.minus(1, intervalUnit);

            // Format date for display
            String dateLabel;
            if (intervalUnit == ChronoUnit.DAYS) {
                dateLabel = periodEnd.toLocalDate().toString();
            } else {
                dateLabel = "هفته " + (dataPoints - i);
            }

            // Calculate metrics for this period

            // 1. Average scores
            List<Submission> periodSubmissions = submissionRepository
                    .findByTimestampBetweenAndExam_Course(periodStart, periodEnd, course);

            double averageScore = periodSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            // 2. Attendance rate
            List<Progress> courseProgress = progressRepository.findByCourse(course);
            long totalStudents = courseProgress.size();

            long activeStudents = courseProgress.stream()
                    .mapToLong(progress -> {
                        List<ActivityLog> studentActivities = activityLogRepository
                                .findByUserAndTimestampBetween(progress.getStudent(), periodStart, periodEnd);
                        return studentActivities.isEmpty() ? 0 : 1;
                    })
                    .sum();

            double attendanceRate = totalStudents > 0 ? (double) activeStudents / totalStudents * 100 : 0;

            // 3. Activity level (based on total activities)
            List<ActivityLog> allActivities = activityLogRepository
                    .findByTimestampBetween(periodStart, periodEnd)
                    .stream()
                    .filter(activity -> {
                        // Filter activities related to this course
                        return courseProgress.stream()
                                .anyMatch(progress -> progress.getStudent().getId().equals(activity.getUser().getId()));
                    })
                    .collect(Collectors.toList());

            double activityLevel = totalStudents > 0 ? (double) allActivities.size() / totalStudents * 10 : 0; // Scale to 0-100
            activityLevel = Math.min(100, activityLevel);

            Map<String, Object> trendPoint = new HashMap<>();
            trendPoint.put("date", dateLabel);
            trendPoint.put("averageScore", Math.round(averageScore * 100.0) / 100.0);
            trendPoint.put("attendanceRate", Math.round(attendanceRate * 100.0) / 100.0);
            trendPoint.put("activityLevel", Math.round(activityLevel * 100.0) / 100.0);

            trends.add(trendPoint);
        }

        result.put("trends", trends);
        result.put("period", period);
        result.put("dataPoints", dataPoints);

        return result;
    }

    /**
     * محاسبه فعالیت در هر درس
     */
    private Map<String, Object> getLessonActivityBreakdown(List<ActivityLog> activities, Course course) {
        Map<String, Map<String, Long>> lessonActivities = new HashMap<>();

        // گروه‌بندی فعالیت‌ها بر اساس درس
        for (ActivityLog activity : activities) {
            String lessonTitle = getLessonTitleFromActivity(activity, course);
            if (lessonTitle != null) {
                lessonActivities.computeIfAbsent(lessonTitle, k -> new HashMap<>())
                        .merge(activity.getActivityType(), 1L, Long::sum);
            }
        }

        Map<String, Object> result = new HashMap<>();
        lessonActivities.forEach((lessonTitle, activityCounts) -> {
            Map<String, Object> lessonData = new HashMap<>();
            lessonData.put("totalActivities", activityCounts.values().stream().mapToLong(Long::longValue).sum());
            lessonData.put("activitiesByType", activityCounts);

            // محاسبه متریک‌های خاص
            lessonData.put("contentViews", activityCounts.getOrDefault("CONTENT_VIEW", 0L));
            lessonData.put("assignments", activityCounts.getOrDefault("ASSIGNMENT_SUBMISSION", 0L));
            lessonData.put("exams", activityCounts.getOrDefault("EXAM_SUBMISSION", 0L));
            lessonData.put("completions", activityCounts.getOrDefault("LESSON_COMPLETION", 0L));

            result.put(lessonTitle, lessonData);
        });

        return result;
    }

    /**
     * دریافت برچسب فارسی برای انواع فعالیت‌ها
     */
    private String getActivityTypeLabel(String activityType) {
        switch (activityType) {
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
                return "فعالیت نامشخص";
        }
    }

    // اضافه کردن این متدها به AnalyticsService.java

    /**
     * دریافت آنالیز پیشرفته فعالیت‌های دانش‌آموز
     */
    public Map<String, Object> getAdvancedStudentAnalytics(Long studentId, Long courseId, String timeFilter) {
        return activityAnalyticsService.getAdvancedStudentAnalytics(studentId, courseId, timeFilter);
    }

    /**
     * محاسبه توزیع انواع فعالیت‌ها
     */
    private Map<String, Object> getActivityTypeDistribution(List<ActivityLog> activities) {
        Map<String, Long> distribution = activities.stream()
//                .filter(activity -> !"CONTENT_VIEW".equals(activity.getActivityType()))
                .filter(activityLog -> !"EXAM_START".equals(activityLog.getActivityType()))
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.counting()
                ));

        long total = activities.stream()
//                .filter(activity -> !"CONTENT_VIEW".equals(activity.getActivityType()))
                .filter(activityLog -> !"EXAM_START".equals(activityLog.getActivityType()))
                .count();
        Map<String, Object> result = new HashMap<>();

        distribution.forEach((type, count) -> {
            Map<String, Object> typeData = new HashMap<>();
            typeData.put("count", count);
            typeData.put("percentage", total > 0 ? Math.round((double) count / total * 100.0) : 0);
            typeData.put("label", getActivityTypeLabel(type));
            result.put(type, typeData);
        });

        return result;
    }

    /**
     * ایجاد Timeline فعالیت‌ها
     */
    private List<Map<String, Object>> getActivityTimeline(List<ActivityLog> activities) {
        return activities.stream()
                .limit(50) // محدود کردن به 50 فعالیت اخیر
                .map(activity -> {
                    Map<String, Object> timelineItem = new HashMap<>();
                    timelineItem.put("id", activity.getId());
                    timelineItem.put("type", activity.getActivityType());
                    timelineItem.put("typeLabel", getActivityTypeLabel(activity.getActivityType()));
                    timelineItem.put("description", generateActivityDescription(activity));
                    timelineItem.put("timestamp", activity.getTimestamp());
                    timelineItem.put("timeSpent", activity.getTimeSpent());

                    // اضافه کردن metadata
                    if (activity.getMetadata() != null && !activity.getMetadata().isEmpty()) {
                        timelineItem.put("metadata", activity.getMetadata());
                    }

                    // اضافه کردن اطلاعات نمره در صورت وجود
                    if ("EXAM_SUBMISSION".equals(activity.getActivityType())) {
                        Optional<Submission> submission = submissionRepository.findById(activity.getRelatedEntityId());
                        submission.ifPresent(s -> timelineItem.put("score", s.getScore()));
                    } else if ("ASSIGNMENT_SUBMISSION".equals(activity.getActivityType())) {
                        Optional<AssignmentSubmission> submission = assignmentSubmissionRepository.findById(activity.getRelatedEntityId());
                        submission.ifPresent(s -> timelineItem.put("score", s.getScore()));
                    }

                    return timelineItem;
                })
                .collect(Collectors.toList());
    }

    /**
     * تحلیل زمان بر اساس نوع فعالیت
     */
    private Map<String, Object> getTimeAnalysisByActivityType(List<ActivityLog> activities) {
        // Sum time spent in seconds first, then convert to minutes for display
        Map<String, Double> timeByTypeInSeconds = activities.stream()
                .filter(activity -> activity.getTimeSpent() != null && activity.getTimeSpent() > 0)
                .filter(activity -> !"CONTENT_VIEW".equals(activity.getActivityType()))
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.summingDouble(activity -> activity.getTimeSpent().doubleValue()) // Keep as seconds
                ));

        double totalTimeInSeconds = timeByTypeInSeconds.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<String, Object> result = new HashMap<>();
        
        // Add total time information
        result.put("totalActivityTimeSeconds", Math.round(totalTimeInSeconds));
        result.put("totalActivityTimeMinutes", Math.round(totalTimeInSeconds / 60.0 * 10.0) / 10.0);
        result.put("totalActivityTimeHours", Math.round(totalTimeInSeconds / 3600.0 * 10.0) / 10.0);
        
        // Add individual activity type data
        timeByTypeInSeconds.forEach((type, timeInSeconds) -> {
            double timeInMinutes = timeInSeconds / 60.0; // Convert to minutes for display
            double timeInHours = timeInSeconds / 3600.0; // Convert to hours for display
            Map<String, Object> typeData = new HashMap<>();
            // Keep backward compatibility
            typeData.put("totalMinutes", Math.round(timeInMinutes * 10.0) / 10.0); // Round to 1 decimal
            typeData.put("totalHours", Math.round(timeInHours * 10.0) / 10.0); // Round to 1 decimal
            // Add fields that frontend expects
            typeData.put("valueSeconds", Math.round(timeInSeconds));
            typeData.put("valueMinutes", Math.round(timeInMinutes * 10.0) / 10.0);
            typeData.put("valueHours", Math.round(timeInHours * 10.0) / 10.0);
            typeData.put("percentage", totalTimeInSeconds > 0 ? Math.round((timeInSeconds / totalTimeInSeconds) * 100.0) : 0);
            typeData.put("label", getActivityTypeLabel(type));
            result.put(type, typeData);
        });

        return result;
    }

    /**
     * دریافت عنوان درس از فعالیت
     */
    private String getLessonTitleFromActivity(ActivityLog activity, Course course) {
        if (activity.getRelatedEntityId() == null) return null;

        try {
            switch (activity.getActivityType()) {
                case "LESSON_COMPLETION":
                case "LESSON_ACCESS":
                    Optional<Lesson> lesson = lessonRepository.findById(activity.getRelatedEntityId());
                    return lesson.map(Lesson::getTitle).orElse(null);

                case "EXAM_SUBMISSION":
                    Optional<Submission> submission = submissionRepository.findById(activity.getRelatedEntityId());
                    if (submission.isPresent()) {
                        return submission.get().getExam().getLesson().getTitle();
                    }
                    break;

                case "ASSIGNMENT_SUBMISSION":
                    Optional<AssignmentSubmission> assignmentSub = assignmentSubmissionRepository.findById(activity.getRelatedEntityId());
                    if (assignmentSub.isPresent()) {
                        return assignmentSub.get().getAssignment().getLesson().getTitle();
                    }
                    break;

                case "CONTENT_VIEW":
                    // اگر metadata شامل lesson title باشد
                    if (activity.getMetadata() != null && activity.getMetadata().containsKey("lessonTitle")) {
                        return activity.getMetadata().get("lessonTitle").toString();
                    }
                    break;
            }
        } catch (Exception e) {
            // در صورت خطا، null برگردان
            return null;
        }

        return null;
    }

    /**
     * دریافت تاریخ شروع بر اساس فیلتر زمانی
     */
    private LocalDateTime getStartDateByFilter(String timeFilter) {
        switch (timeFilter) {
            case "week":
                return AnalyticsUtils.getIranTimeMinusDays(7);
            case "month":
                return AnalyticsUtils.getIranTimeMinusMonths(1);
            case "3months":
                return AnalyticsUtils.getIranTimeMinusMonths(3);
            case "semester":
                return AnalyticsUtils.getIranTimeMinusMonths(6);
            default:
                return AnalyticsUtils.getIranTimeMinusDays(14);
        }
    }


    /**
     * دریافت داده‌های Heatmap فعالیت روزانه دانش‌آموز
     */
    public Map<String, Object> getStudentDailyHeatmap(Long studentId, Long courseId, String timeFilter) {
        return activityAnalyticsService.getStudentDailyHeatmap(studentId, courseId, timeFilter);
    }

    /**
     * دریافت Timeline فعالیت‌های دانش‌آموز
     */
    public Map<String, Object> getStudentActivityTimeline(Long studentId, Long courseId, String timeFilter, int limit) {
        return activityAnalyticsService.getStudentActivityTimeline(studentId, courseId, timeFilter, limit);
    }

    /**
     * ایجاد داده‌های Heatmap برای 7 روز هفته × 24 ساعت
     */
    private List<Map<String, Object>> createHeatmapData(List<ActivityLog> activities) {
        List<Map<String, Object>> heatmapData = new ArrayList<>();

        // گروه‌بندی فعالیت‌ها بر اساس روز هفته و ساعت
        Map<String, Long> activityCounts = activities.stream()
                .collect(Collectors.groupingBy(
                        activity -> {
                            LocalDateTime timestamp = activity.getTimestamp();
                            int dayOfWeek = (timestamp.getDayOfWeek().getValue() +1)% 7; // شنبه = 0
                            int hour = timestamp.getHour();
                            return dayOfWeek + "-" + hour;
                        },
                        Collectors.counting()
                ));

        // ایجاد داده برای هر ترکیب روز-ساعت
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                String key = day + "-" + hour;
                long count = activityCounts.getOrDefault(key, 0L);

                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("dayOfWeek", day);
                dataPoint.put("hour", hour);
                dataPoint.put("activityCount", count);
                dataPoint.put("dayName", AnalyticsUtils.getDayName(day));

                heatmapData.add(dataPoint);
            }
        }

        return heatmapData;
    }

    /**
     * تحلیل داده‌های Heatmap
     */
    private Map<String, Object> analyzeHeatmapData(List<Map<String, Object>> heatmapData) {
        Map<String, Object> analytics = new HashMap<>();

        // یافتن فعال‌ترین روز و ساعت
        Map<String, Object> mostActivePoint = heatmapData.stream()
                .max(Comparator.comparingLong(d -> (Long) d.get("activityCount")))
                .orElse(new HashMap<>());

        if (!mostActivePoint.isEmpty()) {
            analytics.put("mostActiveDay", AnalyticsUtils.getDayName((Integer) mostActivePoint.get("dayOfWeek")));
            analytics.put("mostActiveHour", mostActivePoint.get("hour") + ":00");
        } else {
            analytics.put("mostActiveDay", "نامشخص");
            analytics.put("mostActiveHour", "نامشخص");
        }

        // محاسبه کل فعالیت‌ها
        long totalActivities = heatmapData.stream()
                .mapToLong(d -> (Long) d.get("activityCount"))
                .sum();
        analytics.put("totalActivities", totalActivities);

        // محاسبه میانگین فعالیت در روز
        Map<Integer, Long> dailyTotals = new HashMap<>();
        for (Map<String, Object> data : heatmapData) {
            int day = (Integer) data.get("dayOfWeek");
            long count = (Long) data.get("activityCount");
            dailyTotals.merge(day, count, Long::sum);
        }

        double avgDailyActivity = dailyTotals.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
        analytics.put("averageDailyActivity", Math.round(avgDailyActivity * 10.0));

        // تشخیص الگوی مطالعه
        String studyPattern = determineStudyPattern(heatmapData);
        analytics.put("studyPattern", studyPattern);

        return analytics;
    }

    /**
     * ایجاد داده‌های Timeline
     */
    private List<Map<String, Object>> createTimelineData(List<ActivityLog> activities) {
        return activities.stream()
                .map(activity -> {
                    Map<String, Object> timelineItem = new HashMap<>();

                    timelineItem.put("id", activity.getId());
                    timelineItem.put("type", activity.getActivityType());
                    timelineItem.put("description", generateActivityDescription(activity));
                    timelineItem.put("timestamp", activity.getTimestamp());
                    timelineItem.put("timeSpent", activity.getTimeSpent() != null ? activity.getTimeSpent() : 0);// Convert seconds to minutes

                    // اضافه کردن metadata
                    if (activity.getMetadata() != null && !activity.getMetadata().isEmpty()) {
                        timelineItem.put("metadata", activity.getMetadata());
                    }

                    // اضافه کردن نمره در صورت وجود
                    if ("EXAM_SUBMISSION".equals(activity.getActivityType())) {
                        Optional<Submission> submission = submissionRepository.findById(activity.getRelatedEntityId());
                        submission.ifPresent(s -> timelineItem.put("score", s.getScore()));
                    } else if ("ASSIGNMENT_SUBMISSION".equals(activity.getActivityType())) {
                        Optional<AssignmentSubmission> submission = assignmentSubmissionRepository.findById(activity.getRelatedEntityId());
                        submission.ifPresent(s -> timelineItem.put("score", s.getScore()));
                    }

                    return timelineItem;
                })
                .collect(Collectors.toList());
    }

    /**
     * ایجاد آمار Timeline
     */
    private Map<String, Object> createTimelineStatistics(List<ActivityLog> activities) {
        Map<String, Object> stats = new HashMap<>();

        // تعداد کل فعالیت‌ها
        stats.put("totalActivities", activities.size());

        // توزیع انواع فعالیت‌ها
        Map<String, Long> activityTypeDistribution = activities.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getActivityType,
                        Collectors.counting()
                ));
        stats.put("activityTypeDistribution", activityTypeDistribution);

        // محاسبه میانگین زمان صرف شده
        double avgTimeSpent = activities.stream()
                .filter(a -> a.getTimeSpent() != null && a.getTimeSpent() > 0)
                .mapToDouble(ActivityLog::getTimeSpent)
                .average()
                .orElse(0.0);
        stats.put("averageTimeSpent", Math.round(avgTimeSpent / 60.0)); // Convert seconds to minutes

        // آخرین فعالیت
        if (!activities.isEmpty()) {
            ActivityLog lastActivity = activities.get(0); // فهرست به ترتیب نزولی است
            stats.put("lastActivityType", lastActivity.getActivityType());
            stats.put("lastActivityTime", lastActivity.getTimestamp());
        }

        // محاسبه فعالیت در روزهای مختلف هفته
        Map<String, Long> weeklyDistribution = activities.stream()
                .collect(Collectors.groupingBy(
                        activity -> AnalyticsUtils.getDayName(activity.getTimestamp().getDayOfWeek().getValue() % 7),
                        Collectors.counting()
                ));
        stats.put("weeklyDistribution", weeklyDistribution);

        return stats;
    }

    /**
     * تشخیص الگوی مطالعه بر اساس داده‌های Heatmap
     */
    private String determineStudyPattern(List<Map<String, Object>> heatmapData) {
        // محاسبه فعالیت در بازه‌های زمانی مختلف
        long morningActivity = heatmapData.stream()
                .filter(d -> {
                    int hour = (Integer) d.get("hour");
                    return hour >= 6 && hour < 12;
                })
                .mapToLong(d -> (Long) d.get("activityCount"))
                .sum();

        long afternoonActivity = heatmapData.stream()
                .filter(d -> {
                    int hour = (Integer) d.get("hour");
                    return hour >= 12 && hour < 18;
                })
                .mapToLong(d -> (Long) d.get("activityCount"))
                .sum();

        long eveningActivity = heatmapData.stream()
                .filter(d -> {
                    int hour = (Integer) d.get("hour");
                    return hour >= 18 && hour < 24;
                })
                .mapToLong(d -> (Long) d.get("activityCount"))
                .sum();

        long nightActivity = heatmapData.stream()
                .filter(d -> {
                    int hour = (Integer) d.get("hour");
                    return hour >= 0 && hour < 6;
                })
                .mapToLong(d -> (Long) d.get("activityCount"))
                .sum();

        // تشخیص الگو
        long maxActivity = Math.max(Math.max(morningActivity, afternoonActivity),
                Math.max(eveningActivity, nightActivity));

        if (maxActivity == morningActivity) {
            return "صبحگاه";
        } else if (maxActivity == afternoonActivity) {
            return "بعدازظهر";
        } else if (maxActivity == eveningActivity) {
            return "عصرگاه";
        } else if (maxActivity == nightActivity) {
            return "شبگرد";
        } else {
            return "نامنظم";
        }
    }

    /**
     * دریافت تاریخ شروع بر اساس فیلتر زمانی
     */
    private LocalDateTime getStartDateByFilterHeatmap(String timeFilter) {
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
     * Helper method برای استخراج نام دوره از ActivityLog
     */
    private String extractCourseName(ActivityLog log) {
        // 1. اول metadata رو چک کن
        if (log.getMetadata() != null && !log.getMetadata().isEmpty()) {
            // چک کردن کلیدهای مختلف که ممکنه نام دوره رو داشته باشن
            if (log.getMetadata().containsKey("courseTitle")) {
                return log.getMetadata().get("courseTitle");
            }
            if (log.getMetadata().containsKey("courseName")) {
                return log.getMetadata().get("courseName");
            }
        }

        // 2. اگر از metadata نتونستیم بگیریم، از relatedEntityId استفاده کن
        if (log.getRelatedEntityId() != null) {
            try {
                switch (log.getActivityType()) {
                    case "LESSON_COMPLETION":
                    case "LESSON_ACCESS":
                        return getLessonCourseName(log.getRelatedEntityId());

                    case "CONTENT_VIEW":
                        return getContentCourseName(log.getRelatedEntityId());

                    case "EXAM_SUBMISSION":
                    case "EXAM_START":
                        return getExamCourseName(log.getRelatedEntityId());

                    case "ASSIGNMENT_SUBMISSION":
                    case "ASSIGNMENT_VIEW":
                        return getAssignmentCourseName(log.getRelatedEntityId());

                    case "FILE_ACCESS":
                        return getFileCourseName(log.getRelatedEntityId());

                    default:
                        // برای سایر انواع فعالیت که مستقیماً به دوره مربوط نیستن
                        return null;
                }
            } catch (Exception e) {
                // در صورت خطا، null برگردان
                return null;
            }
        }

        return null;
    }

    /**
     * دریافت نام دوره از طریق درس
     */
    private String getLessonCourseName(Long lessonId) {
        try {
            Optional<Lesson> lesson = lessonRepository.findById(lessonId);
            if (lesson.isPresent() && lesson.get().getCourse() != null) {
                return lesson.get().getCourse().getTitle();
            }
        } catch (Exception e) {
            // در صورت خطا یا نبود، null برگردان
        }
        return null;
    }

    /**
     * دریافت نام دوره از طریق محتوا
     */
    private String getContentCourseName(Long contentId) {
        try {
            Optional<Content> content = contentRepository.findById(contentId);
            if (content.isPresent() && content.get().getLesson() != null
                    && content.get().getLesson().getCourse() != null) {
                return content.get().getLesson().getCourse().getTitle();
            }
        } catch (Exception e) {
            // در صورت خطا یا نبود، null برگردان
        }
        return null;
    }

    /**
     * دریافت نام دوره از طریق آزمون
     */
    private String getExamCourseName(Long examId) {
        try {
            // برای EXAM_SUBMISSION، relatedEntityId ممکنه submission id باشه
            Optional<Submission> submission = submissionRepository.findById(examId);
            if (submission.isPresent() && submission.get().getExam() != null
                    && submission.get().getExam().getLesson() != null
                    && submission.get().getExam().getLesson().getCourse() != null) {
                return submission.get().getExam().getLesson().getCourse().getTitle();
            }

            // یا ممکنه مستقیماً exam id باشه
            Optional<Exam> exam = examRepository.findById(examId);
            if (exam.isPresent() && exam.get().getLesson() != null
                    && exam.get().getLesson().getCourse() != null) {
                return exam.get().getLesson().getCourse().getTitle();
            }
        } catch (Exception e) {
            // در صورت خطا یا نبود، null برگردان
        }
        return null;
    }

    /**
     * دریافت نام دوره از طریق تکلیف
     */
    private String getAssignmentCourseName(Long assignmentId) {
        try {
            // برای ASSIGNMENT_SUBMISSION، relatedEntityId ممکنه submission id باشه
            Optional<AssignmentSubmission> assignmentSub = assignmentSubmissionRepository.findById(assignmentId);
            if (assignmentSub.isPresent() && assignmentSub.get().getAssignment() != null
                    && assignmentSub.get().getAssignment().getLesson() != null
                    && assignmentSub.get().getAssignment().getLesson().getCourse() != null) {
                return assignmentSub.get().getAssignment().getLesson().getCourse().getTitle();
            }

            // یا ممکنه مستقیماً assignment id باشه
            Optional<Assignment> assignment = assignmentRepository.findById(assignmentId);
            if (assignment.isPresent() && assignment.get().getLesson() != null
                    && assignment.get().getLesson().getCourse() != null) {
                return assignment.get().getLesson().getCourse().getTitle();
            }
        } catch (Exception e) {
            // در صورت خطا یا نبود، null برگردان
        }
        return null;
    }

    /**
     * دریافت نام دوره از طریق فایل (اگر فایل به درسی مرتبط باشه)
     */
    private String getFileCourseName(Long fileId) {
        try {
            // اینجا باید بر اساس ساختار فایل‌های سیستم کدنویسی کنی
            // فعلاً فرض می‌کنیم که این اطلاعات در metadata موجوده
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Method جدید برای دریافت فعالیت‌های دانش‌آموز
     */
    public Map<String, Object> getMyActivities(Long studentId, Long courseId, String timeFilter, int limit) {
        return activityAnalyticsService.getMyActivities(studentId, courseId, timeFilter, limit);
    }

    /**
     * Get daily activity data for student charts
     */
    public Map<String, Object> getStudentDailyActivity(Long studentId, Long courseId, int days) {
        return activityAnalyticsService.getStudentDailyActivity(studentId, courseId, days);
    }

    /**
     * Get activity summary statistics for student
     */
    public Map<String, Object> getStudentActivitySummary(Long studentId, Long courseId, String timeFilter) {
        return activityAnalyticsService.getStudentActivitySummary(studentId, courseId, timeFilter);
    }

    /**
     * ساخت توضیحات فعالیت
     */
    private String createActivityDescription(ActivityLog activity) {
        String baseDescription = getActivityTypeLabel(activity.getActivityType());

        if (activity.getMetadata() != null) {
            Map<String, String> meta = activity.getMetadata();

            switch (activity.getActivityType()) {
                case "CONTENT_VIEW":
                    if (meta.containsKey("contentTitle")) {
                        return "مشاهده محتوای: " + meta.get("contentTitle");
                    }
                    break;
                case "LESSON_COMPLETION":
                    if (meta.containsKey("lessonTitle")) {
                        return "تکمیل درس: " + meta.get("lessonTitle");
                    }
                    break;
                case "EXAM_SUBMISSION":
                    if (meta.containsKey("examTitle")) {
                        return "شرکت در آزمون: " + meta.get("examTitle");
                    }
                    break;
                case "ASSIGNMENT_SUBMISSION":
                    if (meta.containsKey("assignmentTitle")) {
                        return "ارسال تکلیف: " + meta.get("assignmentTitle");
                    }
                    break;
            }
        }

        return baseDescription;
    }
    public Map<String, Object> getStudentGradesDistribution(Long studentId, Long courseId, String timeFilter) {
        return scoreAnalyticsService.getStudentGradesDistribution(studentId, courseId, timeFilter);
    }

    /**
     * Calculate course completion progress based on granular activities 
     * (content viewing/completion, exam submissions, assignment submissions)
     * This method provides fine-grained progress tracking
     */
    private double calculateProgressFromActivities(User student, Course course) {
        // Get all lessons in the course
        List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
        if (lessons.isEmpty()) return 0.0;

        // Get student's progress record
        Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
        
        // Initialize counters for granular activities
        int totalActivities = 0;
        int completedActivities = 0;

        for (Lesson lesson : lessons) {
            // 1. COUNT AND CHECK CONTENT ACTIVITIES
            List<Content> lessonContents = contentRepository.findByLessonOrderByOrderIndex(lesson);
            totalActivities += lessonContents.size();
            
            if (progressOpt.isPresent()) {
                Progress progress = progressOpt.get();
                // Count completed content (either viewed or explicitly completed)
                for (Content content : lessonContents) {
                    if (progress.getCompletedContent().contains(content.getId()) || 
                        progress.getViewedContent().contains(content.getId())) {
                        completedActivities++;
                    }
                }
            }

            // 2. COUNT AND CHECK EXAM ACTIVITIES
            if (examRepository.findByLessonId(lesson.getId()).isPresent()) {
                totalActivities++;
                
                Exam exam = examRepository.findByLessonId(lesson.getId()).get();
                Optional<Submission> submission = submissionRepository.findByStudentAndExam(student, exam);
                if (submission.isPresent()) {
                    // Count any exam submission (regardless of pass/fail for progress tracking)
                    completedActivities++;
                }
            }
            // 3. COUNT AND CHECK ASSIGNMENT ACTIVITIES
            List<Assignment> lessonAssignments = assignmentRepository.findByLesson(lesson);
            totalActivities += lessonAssignments.size();
            
            for (Assignment assignment : lessonAssignments) {
                Optional<AssignmentSubmission> submission = 
                    assignmentSubmissionRepository.findByStudentAndAssignment(student, assignment);
                if (submission.isPresent()) {
                    completedActivities++;
                }
            }
        }

        // Calculate granular progress percentage
        if (totalActivities == 0) {
            return 0.0; // No activities in course
        }

        return Math.min(100.0, (double) completedActivities / totalActivities * 100);
    }
    private Map<String, Object> parseSubmissionAnswers(String answersJson) {
        if (answersJson == null || answersJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(answersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            System.err.println("Error parsing answers JSON: " + e.getMessage());
            return new HashMap<>();
        }
    }
}