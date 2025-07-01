package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class AnalyticsService {

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
            AssignmentRepository assignmentRepository) {
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
    }

    /**
     * Get participation metrics for course students
     */
    public List<Map<String, Object>> getParticipationMetrics(Long courseId) {
        List<Map<String, Object>> participationMetrics = new ArrayList<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // For each enrolled student
        for (User student : course.getEnrolledStudents()) {
            Map<String, Object> studentData = new HashMap<>();

            studentData.put("studentId", student.getId());
            studentData.put("studentName", student.getFirstName() + " " + student.getLastName());

            // Get student's progress
            Progress progress = progressRepository.findByStudentAndCourse(student, course)
                    .orElse(null);

            if (progress != null) {
                studentData.put("viewedContent", progress.getViewedContent().size());
                studentData.put("completedLessons", progress.getCompletedLessons().size());
                studentData.put("lastAccessed", progress.getLastAccessed());
            } else {
                studentData.put("viewedContent", 0);
                studentData.put("completedLessons", 0);
                studentData.put("lastAccessed", null);
            }

            // Get student's exam/assignment activity
            List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("examsTaken", examSubmissions.size());

            // Get assignment submissions
            List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("assignmentSubmissions", assignmentSubmissions.size());

            // Calculate overall participation score
            int totalItems = 0;
            int participatedItems = 0;

            // Count total lessons
            int totalLessons = course.getLessons().size();
            totalItems += totalLessons;

            // Count completed lessons
            int completedLessons = progress != null ? progress.getCompletedLessons().size() : 0;
            participatedItems += completedLessons;

            // Count exams
            int totalExams = course.getLessons().stream()
                    .map(Lesson::getExam)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())
                    .size();
            totalItems += totalExams;

            // Count taken exams
            participatedItems += examSubmissions.size();

            // Count assignments
            int totalAssignments = course.getLessons().stream()
                    .flatMap(lesson -> assignmentRepository.findByLessonId(lesson.getId()).stream())
                    .collect(Collectors.toList())
                    .size();
            totalItems += totalAssignments;

            // Count submitted assignments
            participatedItems += assignmentSubmissions.size();

            // Calculate participation rate
            double participationRate = totalItems > 0 ?
                    (double) participatedItems / totalItems * 100 : 0;

            studentData.put("participationRate", participationRate);

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
     * Get detailed exam analytics for a student
     */
    public Map<String, Object> getExamDetails(User student, Long examId) {
        Map<String, Object> examDetails = new HashMap<>();

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        // Find student's submission for this exam
        Optional<Submission> submissionOpt = submissionRepository.findByStudentAndExam(student, exam);

        if (submissionOpt.isEmpty()) {
            throw new RuntimeException("Student has not taken this exam");
        }

        Submission submission = submissionOpt.get();

        // Basic exam info
        examDetails.put("examId", exam.getId());
        examDetails.put("examTitle", exam.getTitle());
        examDetails.put("studentScore", submission.getScore());
        examDetails.put("passingScore", exam.getPassingScore());
        examDetails.put("passed", submission.isPassed());
        examDetails.put("submissionTime", submission.getSubmissionTime());

        // Get question-level analysis
        List<Map<String, Object>> questionAnalysis = new ArrayList<>();

        List<Question> questions = questionRepository.findByExamOrderById(exam);

        for (Question question : questions) {
            Map<String, Object> questionData = new HashMap<>();

            questionData.put("questionId", question.getId());
            questionData.put("questionText", question.getText());
            questionData.put("points", question.getPoints());

            // Get student's answer
            Long answerId = submission.getAnswers().get(question.getId());

            if (answerId != null) {
                // Find the answer object
                Optional<Answer> answerOpt = question.getAnswers().stream()
                        .filter(a -> a.getId().equals(answerId))
                        .findFirst();

                if (answerOpt.isPresent()) {
                    Answer answer = answerOpt.get();
                    questionData.put("studentAnswer", answer.getText());
                    questionData.put("correct", answer.getCorrect());
                    questionData.put("pointsEarned", answer.getCorrect() ? question.getPoints() : 0);
                }
            } else {
                questionData.put("studentAnswer", "Not answered");
                questionData.put("correct", false);
                questionData.put("pointsEarned", 0);
            }

            // Add correct answer for reference
            Optional<Answer> correctAnswerOpt = question.getAnswers().stream()
                    .filter(Answer::getCorrect)
                    .findFirst();

            if (correctAnswerOpt.isPresent()) {
                questionData.put("correctAnswer", correctAnswerOpt.get().getText());
            }

            questionAnalysis.add(questionData);
        }

        examDetails.put("questions", questionAnalysis);

        // Compare with class average
        List<Submission> allSubmissions = submissionRepository.findByExam(exam);

        double classAverageScore = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        long totalSubmissions = allSubmissions.size();
        long passedSubmissions = allSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        double classPassRate = totalSubmissions > 0 ?
                (double) passedSubmissions / totalSubmissions * 100 : 0;

        examDetails.put("classAverageScore", classAverageScore);
        examDetails.put("classPassRate", classPassRate);
        examDetails.put("percentile", calculatePercentile(submission.getScore(),
                allSubmissions.stream().mapToDouble(Submission::getScore).toArray()));

        return examDetails;
    }

    /**
     * Get detailed analysis for a specific student in a course
     */
    public Map<String, Object> getStudentDetailedAnalysis(Long studentId, Long courseId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Progress progress = progressRepository.findByStudentAndCourse(student, course)
                .orElse(null);

        Map<String, Object> analysis = new HashMap<>();

        if (progress != null) {
            analysis.put("completionPercentage", progress.getCompletionPercentage());
            analysis.put("totalStudyTime", progress.getTotalStudyTime() != null ? progress.getTotalStudyTime() : 0L);
            analysis.put("streak", progress.getCurrentStreak() != null ? progress.getCurrentStreak() : 0);
            analysis.put("lastAccessed", progress.getLastAccessed());
            analysis.put("completedLessons", progress.getCompletedLessons().size());
            analysis.put("totalLessons", progress.getTotalLessons() != null ? progress.getTotalLessons() : 0);
        } else {
            analysis.put("completionPercentage", 0.0);
            analysis.put("totalStudyTime", 0L);
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

        analysis.put("averageTimePerLesson", completedLessons > 0 ?
                Math.round((totalStudyTime / completedLessons) * 10.0) / 10.0 : 0);
        analysis.put("averageTimePerExam", examSubmissions.isEmpty() ? 0 :
                Math.round(examSubmissions.stream().mapToLong(s -> s.getTimeSpent() != null ? s.getTimeSpent() : 0L)
                        .average().orElse(0.0) * 10.0) / 10.0);

        // Calculate class rank
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        long betterStudents = allProgress.stream()
                .filter(p -> p.getCompletionPercentage() > (progress != null ? progress.getCompletionPercentage() : 0))
                .count();

        analysis.put("classRank", betterStudents + 1);
        analysis.put("totalStudents", allProgress.size());

        return analysis;
    }

    /**
     * Get activity timeline for a student
     */
    public List<Map<String, Object>> getStudentActivityTimeline(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<ActivityLog> activities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, thirtyDaysAgo, LocalDateTime.now());

        return activities.stream().map(activity -> {
            Map<String, Object> activityData = new HashMap<>();
            activityData.put("type", activity.getActivityType());
            activityData.put("timestamp", activity.getTimestamp());
            activityData.put("timeSpent", activity.getTimeSpent());
            activityData.put("description", generateActivityDescription(activity));

            // Add score if it's an exam or assignment submission
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

    /**
     * Get exam performance for a student
     */
    public List<Map<String, Object>> getStudentExamPerformance(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<Submission> submissions = submissionRepository.findByStudent(student);

        return submissions.stream().map(submission -> {
            Map<String, Object> examData = new HashMap<>();
            Exam exam = submission.getExam();

            examData.put("examName", exam.getTitle());
            examData.put("score", submission.getScore());
            examData.put("timeSpent", submission.getTimeSpent() != null ?
                    Math.round(submission.getTimeSpent()  * 10.0) / 10.0 : 0.0);
            examData.put("passed", submission.isPassed());
            examData.put("date", submission.getSubmissionTime());

            // Calculate class average for this exam
            List<Submission> allSubmissions = submissionRepository.findByExam(exam);
            double classAverage = allSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            examData.put("classAverage", classAverage);

            return examData;
        }).collect(Collectors.toList());
    }

    /**
     * Get time analysis for a student
     */
    public List<Map<String, Object>> getStudentTimeAnalysis(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<ActivityLog> activities = activityLogRepository.findByUserAndTimestampBetweenOrderByTimestampDesc(
                student, LocalDateTime.now().minusDays(90), LocalDateTime.now());

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
            double efficiency = calculateEfficiency(activityType, avgTime);
            contentTypeData.put("efficiency", efficiency);

            timeAnalysis.add(contentTypeData);
        }

        return timeAnalysis;
    }

    /**
     * Get system overview for teacher
     */
    public Map<String, Object> getSystemOverview(User teacher) {
        Map<String, Object> overview = new HashMap<>();

        // Get teacher's courses
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        // Count total students across all teacher's courses
        int totalStudents = teacherCourses.stream()
                .mapToInt(course -> course.getEnrolledStudents().size())
                .sum();

        // Calculate average completion across all courses
        List<Progress> allProgress = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<Progress> courseProgress = progressRepository.findAll().stream()
                    .filter(p -> p.getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allProgress.addAll(courseProgress);
        }

        double averageCompletion = allProgress.stream()
                .mapToDouble(Progress::getCompletionPercentage)
                .average()
                .orElse(0.0);

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

        // Calculate total study hours
        long totalHours = allProgress.stream()
                .mapToLong(p -> p.getTotalStudyTime() != null ? p.getTotalStudyTime() : 0L)
                .sum() ; // Convert seconds to hours

        overview.put("totalStudents", totalStudents);
        overview.put("totalCourses", teacherCourses.size());
        overview.put("averageCompletion", averageCompletion);
        overview.put("averageScore", averageScore);
        overview.put("totalHours", totalHours);
        overview.put("avgTimePerStudent", totalStudents > 0 ? (double) totalHours / totalStudents : 0);
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
            contentData.put("difficulty", getDifficultyLabel(avgTime));
            contentData.put("studentCount", activities.size());

            // Calculate completion rate and engagement
            contentData.put("completionRate", calculateCompletionRateForContentType(contentType, teacherCourses));
            contentData.put("engagement", calculateEngagementForContentType(contentType, activities));

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

            // Count questions by difficulty (assuming you have difficulty field)
            int easyQuestions = (int) questions.stream().filter(q -> q.getDifficulty() != null && q.getDifficulty() <= 2.0).count();
            int mediumQuestions = (int) questions.stream().filter(q -> q.getDifficulty() != null && q.getDifficulty() > 2.0 && q.getDifficulty() <= 4.0).count();
            int hardQuestions = (int) questions.stream().filter(q -> q.getDifficulty() != null && q.getDifficulty() > 4.0).count();

            topicData.put("easyQuestions", easyQuestions);
            topicData.put("mediumQuestions", mediumQuestions);
            topicData.put("hardQuestions", hardQuestions);

            // Calculate average score and time for questions in this topic
            double avgScore = calculateAverageScoreForQuestions(questions);
            double avgTime = calculateAverageTimeForQuestions(questions);
            double difficultyRating = calculateDifficultyRating(questions);

            topicData.put("avgScore", avgScore);
            topicData.put("avgTime", avgTime);
            topicData.put("difficultyRating", difficultyRating);

            difficultyAnalysis.add(topicData);
        }

        return difficultyAnalysis;
    }

    /**
     * Get lesson performance analysis
     */
    public List<Map<String, Object>> getLessonPerformanceAnalysis(User teacher) {
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);
        List<Map<String, Object>> lessonPerformance = new ArrayList<>();

        for (Course course : teacherCourses) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);

            for (Lesson lesson : lessons) {
                Map<String, Object> lessonData = new HashMap<>();
                lessonData.put("lesson", lesson.getTitle());

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
                lessonData.put("difficulty", getDifficultyLabel(avgTime));
                lessonData.put("studentFeedback", 4.5); // Placeholder - you'd need feedback system

                lessonPerformance.add(lessonData);
            }
        }

        return lessonPerformance;
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

    /**
     * Get challenging questions
     */
    public List<Map<String, Object>> getChallengingQuestions(User teacher) {
        List<Question> teacherQuestions = questionRepository.findByTeacher(teacher);
        List<Map<String, Object>> challengingQuestions = new ArrayList<>();

        for (Question question : teacherQuestions) {
            // Get all submissions for this question
            List<Submission> submissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getAnswers().containsKey(question.getId()))
                    .collect(Collectors.toList());

            if (submissions.isEmpty()) continue;

            // Calculate correct rate
            long correctAnswers = submissions.stream()
                    .filter(s -> {
                        Long answerId = s.getAnswers().get(question.getId());
                        if (answerId == null) return false;

                        return question.getAnswers().stream()
                                .filter(a -> a.getId().equals(answerId))
                                .findFirst()
                                .map(Answer::getCorrect)
                                .orElse(false);
                    })
                    .count();

            double correctRate = (double) correctAnswers / submissions.size() * 100;

            // Only include challenging questions (low correct rate)
            if (correctRate < 70) {
                Map<String, Object> questionData = new HashMap<>();
                questionData.put("id", question.getId());
                questionData.put("text", question.getText());
                questionData.put("difficulty", 100 - correctRate);
                questionData.put("correctRate", correctRate);
                questionData.put("attempts", submissions.size());
                questionData.put("topic", "General");

                // Calculate average time spent on this question
                double avgTime = submissions.stream()
                        .mapToLong(s -> s.getTimeSpent() != null ? s.getTimeSpent() : 0L)
                        .average()
                        .orElse(0.0);

                questionData.put("avgTime", avgTime);
                challengingQuestions.add(questionData);
            }
        }

        // Sort by difficulty (lowest correct rate first)
        challengingQuestions.sort((q1, q2) -> {
            Double rate1 = (Double) q1.get("correctRate");
            Double rate2 = (Double) q2.get("correctRate");
            return rate1.compareTo(rate2);
        });

        return challengingQuestions;
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
            recentActivities.add(activityData);
        }

        return recentActivities;
    }


    private double calculateEfficiency(String activityType, double avgTime) {

        switch (activityType) {
            case "CONTENT_VIEW":
                return avgTime < 1200 ? 95 : (avgTime < 2400 ? 85 : 70);
            case "ASSIGNMENT_SUBMISSION":
                return avgTime < 1800 ? 90 : (avgTime < 3600 ? 80 : 65);
            default:
                return 80.0;
        }
    }

    private String getDifficultyLabel(double avgTime) {
        if (avgTime < 1200) return "آسان";      // 20 seconds = 1200 seconds
        if (avgTime < 2700) return "متوسط";     // 45 seconds = 2700 seconds
        if (avgTime < 4200) return "سخت";       // 70 seconds = 4200 seconds
        return "خیلی سخت";
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

    private double calculateCompletionRateForContentType(String contentType, List<Course> courses) {
        // Placeholder implementation
        return 85.0; // 85% completion rate
    }

    private double calculateEngagementForContentType(String contentType, List<ActivityLog> activities) {
        // محاسبه engagement بر اساس frequency و زمان صرف شده
        if (activities.isEmpty()) return 0.0;

        double avgTimeSpent = activities.stream()
                .mapToLong(ActivityLog::getTimeSpent)
                .average()
                .orElse(0.0);

        // Higher time spent = higher engagement (up to a point) - اکنون در ثانیه
        return Math.min(95.0, 50 + (avgTimeSpent / 60)); // تقسیم بر 60 برای تنظیم scale
    }

    private double calculateAverageScoreForQuestions(List<Question> questions) {
        // Get all submissions for these questions and calculate average score
        return 75.0; // Placeholder
    }

    private double calculateAverageTimeForQuestions(List<Question> questions) {
        // Calculate average time spent on these questions in seconds
        return 210.0; // Placeholder - 3.5 seconds = 210 seconds
    }

    private double calculateDifficultyRating(List<Question> questions) {
        // Calculate difficulty rating based on correct rates and time spent
        return 3.2; // Placeholder - on scale of 1-5
    }

    private int calculateTrend(List<ActivityLog> activities, int days) {
        if (activities.size() < days) return 0;

        // Compare first half with second half of the period
        int halfDays = days / 2;
        LocalDateTime midPoint = LocalDateTime.now().minusDays(halfDays);

        long recentCount = activities.stream()
                .filter(a -> a.getTimestamp().isAfter(midPoint))
                .count();

        long earlierCount = activities.stream()
                .filter(a -> a.getTimestamp().isBefore(midPoint))
                .count();

        if (earlierCount == 0) return 0;

        return (int) (((double) recentCount - earlierCount) / earlierCount * 100);
    }

    private double calculatePercentile(double value, double[] values) {
        if (values.length == 0) return 0;

        int count = 0;
        for (double v : values) {
            if (v < value) count++;
        }

        return (double) count / values.length * 100;
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
        performance.put("averageCompletion", Math.round(averageCompletion * 10.0) / 10.0);
        performance.put("totalStudyTime", Math.round(totalStudyTimeFromActivities  * 10.0) / 10.0); // Convert seconds to hours
        performance.put("averageStudyTimePerCourse", studentCourses.isEmpty() ? 0 :
                Math.round((totalStudyTimeFromActivities / studentCourses.size())  * 10.0) / 10.0);

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
        performance.put("averageExamScore", Math.round(averageExamScore * 10.0) / 10.0);
        performance.put("examPassRate", examSubmissions.isEmpty() ? 0 :
                Math.round((double) passedExams / examSubmissions.size() * 100 * 10.0) / 10.0);

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
        performance.put("averageAssignmentScore", Math.round(averageAssignmentScore * 10.0) / 10.0);

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
            courseData.put("studyTime", Math.round(courseStudyTime * 10.0) / 10.0);

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
            List<Progress> courseProgress = progressRepository.findAll().stream()
                    .filter(p -> p.getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());

            allStudentProgress.addAll(courseProgress);
            allStudents.addAll(course.getEnrolledStudents());
        }

        // Calculate statistics
        int totalStudents = allStudents.size();
        int activeStudents = (int) allStudentProgress.stream()
                .filter(p -> p.getLastAccessed() != null &&
                        p.getLastAccessed().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        double averageCompletion = allStudentProgress.stream()
                .mapToDouble(Progress::getCompletionPercentage)
                .average()
                .orElse(0.0);

        long completedStudents = allStudentProgress.stream()
                .filter(p -> p.getCompletionPercentage() >= 100)
                .count();

        // Get exam statistics
        List<Submission> allSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<Submission> courseSubmissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allSubmissions.addAll(courseSubmissions);
        }

        double averageExamScore = allSubmissions.stream()
                .mapToDouble(Submission::getScore)
                .average()
                .orElse(0.0);

        long passedExams = allSubmissions.stream()
                .filter(Submission::isPassed)
                .count();

        // Get assignment statistics
        List<AssignmentSubmission> allAssignmentSubmissions = new ArrayList<>();
        for (Course course : teacherCourses) {
            List<AssignmentSubmission> courseAssignments = assignmentSubmissionRepository.findAll().stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
                    .collect(Collectors.toList());
            allAssignmentSubmissions.addAll(courseAssignments);
        }

        // Build overview
        overview.put("totalStudents", totalStudents);
        overview.put("activeStudents", activeStudents);
        overview.put("inactiveStudents", totalStudents - activeStudents);
        overview.put("averageCompletion", Math.round(averageCompletion * 10.0) / 10.0);
        overview.put("completedStudents", completedStudents);
        overview.put("totalCourses", teacherCourses.size());
        overview.put("totalExamsTaken", allSubmissions.size());
        overview.put("averageExamScore", Math.round(averageExamScore * 10.0) / 10.0);
        overview.put("examPassRate", allSubmissions.isEmpty() ? 0 :
                Math.round((double) passedExams / allSubmissions.size() * 100 * 10.0) / 10.0);
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
            studentData.put("averageScore", Math.round(averageScore * 10.0) / 10.0);

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

    // Additional methods for comprehensive report and other analytics
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
                    Math.round(activity.getTimeSpent() * 10.0) / 10.0 : 0.0);
            activityData.put("description", generateActivityDescription(activity));

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
        stats.put("averageScore", Math.round(averageScore * 10.0) / 10.0);

        // درصد تکمیل دوره
        int totalLessons = course.getLessons().size();
        int completedLessons = progress != null ? progress.getCompletedLessons().size() : 0;
        double completionRate = totalLessons > 0 ? (double) completedLessons / totalLessons * 100 : 0;
        stats.put("completionRate", Math.round(completionRate * 10.0) / 10.0);

        // مجموع ساعات مطالعه
        long totalStudyseconds = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, LocalDateTime.now().minusDays(90), LocalDateTime.now())
                .stream()
                .filter(log -> log.getRelatedEntityId() != null)
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
        stats.put("totalStudyHours", Math.round(totalStudyseconds  * 10.0) / 10.0);

        // امتیاز پایداری (بر اساس فعالیت روزانه)
        double consistencyScore = calculateConsistencyScore(student, 30);
        stats.put("consistencyScore", Math.round(consistencyScore * 10.0) / 10.0);

        // رتبه در کلاس
        List<Progress> allProgress = progressRepository.findAll()
                .stream()
                .filter(p -> p.getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

        long betterStudents = allProgress.stream()
                .filter(p -> p.getCompletionPercentage() > (progress != null ? progress.getCompletionPercentage() : 0))
                .count();
        stats.put("classRank", (int) (betterStudents + 1));
        stats.put("totalStudents", allProgress.size());

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
            dayData.put("dayName", getDayName(dayStart.getDayOfWeek()));
            dayData.put("views", countActivitiesByType(dayActivities, "CONTENT_VIEW"));
            dayData.put("submissions", countActivitiesByType(dayActivities, "EXAM_SUBMISSION", "ASSIGNMENT_SUBMISSION"));
            dayData.put("completions", countActivitiesByType(dayActivities, "LESSON_COMPLETION"));
            dayData.put("totalTime", Math.round(dayActivities.stream()
                    .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                    .sum() * 10.0) / 10.0);

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
                    item.put("value", entry.getValue());
                    item.put("hours", Math.round(entry.getValue() * 10.0) / 10.0);
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
            monthData.put("month", getMonthName(monthStart.getMonthValue()));
            monthData.put("year", monthStart.getYear());
            monthData.put("lessons", countActivitiesByType(monthActivities, "LESSON_COMPLETION"));
            monthData.put("exams", countActivitiesByType(monthActivities, "EXAM_SUBMISSION"));
            monthData.put("assignments", countActivitiesByType(monthActivities, "ASSIGNMENT_SUBMISSION"));
            monthData.put("totalActivities", monthActivities.size());

            trend.add(monthData);
        }

        return trend;
    }

    private long countActivitiesByType(List<ActivityLog> activities, String... types) {
        return activities.stream()
                .filter(activity -> Arrays.asList(types).contains(activity.getActivityType()))
                .count();
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

    private String getDayName(DayOfWeek dayOfWeek) {
        String[] dayNames = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه"};
        return dayNames[dayOfWeek.getValue() % 7];
    }

    private String getMonthName(int month) {
        String[] monthNames = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        return monthNames[month - 1];
    }

    // Additional methods for course analytics

    /**
     * Get course time distribution for students
     */
    public Map<String, Object> getCourseTimeDistribution(Long courseId, String period, String granularity) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(endDate, period);

        // دریافت activity logs مربوط به این course
        List<ActivityLog> activities = activityLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().isAfter(startDate) && log.getTimestamp().isBefore(endDate))
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .collect(Collectors.toList());

        // Group by student
        Map<Long, List<ActivityLog>> activitiesByStudent = activities.stream()
                .collect(Collectors.groupingBy(log -> log.getUser().getId()));

        // محاسبه total time per student
        Map<Long, Long> timePerStudent = new HashMap<>();
        for (Map.Entry<Long, List<ActivityLog>> entry : activitiesByStudent.entrySet()) {
            Long totalTime = entry.getValue().stream()
                    .mapToLong(ActivityLog::getTimeSpent)
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

        // Timeline data
        List<Map<String, Object>> timeline = new ArrayList<>();
        if ("daily".equals(granularity)) {
            timeline = createDailyTimeline(activities, startDate, endDate);
        } else if ("weekly".equals(granularity)) {
            timeline = createWeeklyTimeline(activities, startDate, endDate);
        }

        // Calculate averages
        long totalStudents = course.getEnrolledStudents().size();
        double averageTimePerStudent = times.isEmpty() ? 0 : times.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

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
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(endDate, period);

        // دریافت activities مربوط به course
        List<ActivityLog> activities = activityLogRepository.findAll().stream()
                .filter(log -> log.getTimestamp().isAfter(startDate) && log.getTimestamp().isBefore(endDate))
                .filter(log -> isCourseRelatedActivity(log, courseId))
                .collect(Collectors.toList());

        long totalStudents = course.getEnrolledStudents().size();

        // Group by activity type
        Map<String, List<ActivityLog>> activitiesByType = activities.stream()
                .collect(Collectors.groupingBy(ActivityLog::getActivityType));

        // Calculate participation metrics
        Map<String, Object> participationMetrics = new HashMap<>();

        // Content Study metrics
        List<ActivityLog> contentActivities = activitiesByType.getOrDefault("CONTENT_VIEW", new ArrayList<>());
        participationMetrics.put("contentStudy", createContentStudyMetrics(contentActivities, totalStudents, courseId));

        // Chat Activity metrics
        List<ActivityLog> chatActivities = activitiesByType.getOrDefault("CHAT_MESSAGE_SEND", new ArrayList<>());
        participationMetrics.put("chatActivity", createChatActivityMetrics(chatActivities, totalStudents));

        // Assignment Submission metrics
        participationMetrics.put("assignmentSubmission", createAssignmentMetrics(courseId, startDate, endDate, totalStudents));

        // Exam Participation metrics
        participationMetrics.put("examParticipation", createExamMetrics(courseId, startDate, endDate, totalStudents));

        // Engagement trend (weekly breakdown)
        List<Map<String, Object>> engagementTrend = new ArrayList<>();
        if (includeTimeline) {
            engagementTrend = createEngagementTrend(activities, startDate, endDate);
        }

        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("totalStudents", totalStudents);
        result.put("participationMetrics", participationMetrics);
        result.put("engagementTrend", engagementTrend);

        return result;
    }

    /**
     * Get course exam scores with filtering and aggregation
     */
    public Map<String, Object> getCourseExamScores(Long courseId, String period, Long examId, boolean includeDetails) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();

        // محاسبه time range بر اساس period
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(endDate, period);

        // دریافت submissions بر اساس course و time range
        List<Submission> submissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .filter(s -> s.getSubmissionTime().isAfter(startDate) && s.getSubmissionTime().isBefore(endDate))
                .filter(s -> examId == null || s.getExam().getId().equals(examId))
                .collect(Collectors.toList());

        if (submissions.isEmpty()) {
            result.put("courseId", courseId);
            result.put("courseName", course.getTitle());
            result.put("period", period);
            result.put("examId", examId);
            result.put("totalSubmissions", 0);
            result.put("message", "No exam submissions found for the specified period");
            return result;
        }

        // محاسبات آماری
        List<Integer> scores = submissions.stream()
                .map(Submission::getScore)
                .collect(Collectors.toList());

        double averageScore = scores.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        int highestScore = scores.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        int lowestScore = scores.stream()
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);

        long passedCount = submissions.stream()
                .filter(Submission::isPassed)
                .count();

        double passRate = submissions.isEmpty() ? 0 : (double) passedCount / submissions.size() * 100;

        // Grade distribution
        Map<String, Integer> gradeDistribution = calculateGradeDistribution(scores);

        // Exam breakdown
        List<Map<String, Object>> examBreakdown = new ArrayList<>();
        if (examId == null) {
            Map<Long, List<Submission>> submissionsByExam = submissions.stream()
                    .collect(Collectors.groupingBy(s -> s.getExam().getId()));

            for (Map.Entry<Long, List<Submission>> entry : submissionsByExam.entrySet()) {
                List<Submission> examSubmissions = entry.getValue();
                Exam exam = examSubmissions.get(0).getExam();

                Map<String, Object> examData = new HashMap<>();
                examData.put("examId", exam.getId());
                examData.put("examTitle", exam.getTitle());
                examData.put("averageScore", examSubmissions.stream()
                        .mapToInt(Submission::getScore)
                        .average()
                        .orElse(0.0));
                examData.put("submissionCount", examSubmissions.size());
                examBreakdown.add(examData);
            }
        }

        // Build result
        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("examId", examId);
        result.put("totalSubmissions", submissions.size());
        result.put("averageScore", Math.round(averageScore * 10.0) / 10.0);
        result.put("highestScore", highestScore);
        result.put("lowestScore", lowestScore);
        result.put("passRate", Math.round(passRate * 10.0) / 10.0);
        result.put("scores", includeDetails ? scores : null);
        result.put("gradeDistribution", gradeDistribution);
        result.put("examBreakdown", examBreakdown);

        return result;
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
            LocalDateTime startDate = calculateStartDate(endDate, period);

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
            lessonData.put("completionRate", Math.round(completionRate * 10.0) / 10.0);
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

    private LocalDateTime calculateStartDate(LocalDateTime endDate, String period) {
        switch (period.toLowerCase()) {
            case "week":
                return endDate.minusWeeks(1);
            case "quarter":
                return endDate.minusMonths(3);
            case "semester":
                return endDate.minusMonths(6);
            case "month":
            default:
                return endDate.minusMonths(1);
        }
    }

    private Map<String, Integer> calculateGradeDistribution(List<Integer> scores) {
        Map<String, Integer> distribution = new HashMap<>();

        for (Integer score : scores) {
            if (score >= 18) {
                distribution.merge("excellent", 1, Integer::sum);
            } else if (score >= 15) {
                distribution.merge("good", 1, Integer::sum);
            } else if (score >= 10) {
                distribution.merge("average", 1, Integer::sum);
            } else {
                distribution.merge("poor", 1, Integer::sum);
            }
        }

        return distribution;
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

        LocalDateTime current = startDate.toLocalDate().atStartOfDay();
        while (!current.isAfter(endDate)) {
            String dateStr = current.toLocalDate().toString();
            timelineMap.put(dateStr, new HashMap<>());
            timelineMap.get(dateStr).put("date", dateStr);
            timelineMap.get(dateStr).put("totalseconds", 0L);
            timelineMap.get(dateStr).put("activeStudents", 0);
            current = current.plusDays(1);
        }

        // Group activities by date
        for (ActivityLog activity : activities) {
            String dateStr = activity.getTimestamp().toLocalDate().toString();
            if (timelineMap.containsKey(dateStr)) {
                Map<String, Object> dayData = timelineMap.get(dateStr);
                Long currentseconds = (Long) dayData.get("totalseconds");
                dayData.put("totalseconds", currentseconds + activity.getTimeSpent());

                // Count unique students (simplified)
                Integer currentStudents = (Integer) dayData.get("activeStudents");
                dayData.put("activeStudents", currentStudents + 1);
            }
        }

        return new ArrayList<>(timelineMap.values());
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
    public List<Map<String, Object>> getDifficultLessons(Long courseId) {
        List<Map<String, Object>> difficultLessons = new ArrayList<>();

        // Fix: Use the correct repository method that accepts Long courseId
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderByOrderIndex(courseId);

        for (Lesson lesson : lessons) {
            Map<String, Object> lessonData = new HashMap<>();
            lessonData.put("lessonId", lesson.getId());
            lessonData.put("lessonTitle", lesson.getTitle());

            // Calculate completion rate for this lesson
            List<Progress> progressList = progressRepository.findAll().stream()
                    .filter(p -> p.getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            long completedCount = progressList.stream()
                    .filter(p -> p.getCompletedLessons().contains(lesson.getId()))
                    .count();

            double completionRate = progressList.isEmpty() ?
                    0 : (double) completedCount / progressList.size() * 100;

            lessonData.put("completionRate", completionRate);

            // If lesson has an exam, get exam performance
            if (lesson.getExam() != null) {
                Exam exam = lesson.getExam();
                List<Submission> examSubmissions = submissionRepository.findByExam(exam);

                double passRate = examSubmissions.isEmpty() ? 0 :
                        (double) examSubmissions.stream().filter(Submission::isPassed).count() /
                                examSubmissions.size() * 100;

                double averageScore = examSubmissions.stream()
                        .mapToDouble(Submission::getScore)
                        .average()
                        .orElse(0.0);

                lessonData.put("examPassRate", passRate);
                lessonData.put("examAverageScore", averageScore);
                lessonData.put("examSubmissions", examSubmissions.size());
            }

            // Check assignment performance for this lesson
            List<Assignment> lessonAssignments = assignmentRepository.findByLessonId(lesson.getId());
            if (!lessonAssignments.isEmpty()) {
                List<AssignmentSubmission> assignmentSubmissions = new ArrayList<>();
                for (Assignment assignment : lessonAssignments) {
                    assignmentSubmissions.addAll(assignmentSubmissionRepository.findByAssignment(assignment));
                }

                double avgAssignmentScore = assignmentSubmissions.stream()
                        .filter(as -> as.getScore() != null)
                        .mapToInt(AssignmentSubmission::getScore)
                        .average()
                        .orElse(0.0);

                lessonData.put("assignmentAverageScore", avgAssignmentScore);
                lessonData.put("assignmentSubmissions", assignmentSubmissions.size());
            }

            // Calculate difficulty score (lower completion and pass rates = higher difficulty)
            double difficultyScore = 100 - completionRate;
            if (lesson.getExam() != null) {
                Exam exam = lesson.getExam();
                List<Submission> examSubmissions = submissionRepository.findByExam(exam);

                double passRate = examSubmissions.isEmpty() ? 0 :
                        (double) examSubmissions.stream().filter(Submission::isPassed).count() /
                                examSubmissions.size() * 100;

                difficultyScore = (difficultyScore + (100 - passRate)) / 2;
            }

            lessonData.put("difficultyScore", difficultyScore);

            difficultLessons.add(lessonData);
        }

        // Sort by difficulty score descending
        difficultLessons.sort((d1, d2) -> {
            Double score1 = (Double) d1.get("difficultyScore");
            Double score2 = (Double) d2.get("difficultyScore");
            return score2.compareTo(score1);
        });

        return difficultLessons;
    }

    /**
     * Identify struggling students based on progress and scores (updated for assignments)
     */
    public List<Map<String, Object>> getStrugglingStudents(Long courseId) {
        List<Map<String, Object>> strugglingStudents = new ArrayList<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Get all progress records for this course
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        for (Progress progress : allProgress) {
            User student = progress.getStudent();
            Map<String, Object> studentData = new HashMap<>();

            studentData.put("studentId", student.getId());
            studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
            studentData.put("completionPercentage", progress.getCompletionPercentage());

            // Get student's exam submissions for this course
            List<Submission> studentSubmissions = submissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            double averageScore = studentSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            long failedExams = studentSubmissions.stream()
                    .filter(s -> !s.isPassed())
                    .count();

            studentData.put("averageExamScore", averageScore);
            studentData.put("examsTaken", studentSubmissions.size());
            studentData.put("failedExams", failedExams);

            // Get student's assignment submissions for this course (instead of exercise)
            List<AssignmentSubmission> studentAssignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            double averageAssignmentScore = studentAssignmentSubmissions.stream()
                    .filter(as -> as.getScore() != null)
                    .mapToInt(AssignmentSubmission::getScore)
                    .average()
                    .orElse(0.0);

            studentData.put("averageAssignmentScore", averageAssignmentScore);
            studentData.put("assignmentSubmissions", studentAssignmentSubmissions.size());

            // Calculate struggle score (lower completion and scores = higher struggle)
            double struggleScore = 100 - progress.getCompletionPercentage();
            if (!studentSubmissions.isEmpty()) {
                double examStruggle = 100 - averageScore;
                struggleScore = (struggleScore + examStruggle) / 2;
            }

            studentData.put("struggleScore", struggleScore);

            strugglingStudents.add(studentData);
        }

        // Sort by struggle score descending (highest struggle first)
        strugglingStudents.sort((s1, s2) -> {
            Double score1 = (Double) s1.get("struggleScore");
            Double score2 = (Double) s2.get("struggleScore");
            return score2.compareTo(score1);
        });

        return strugglingStudents;
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
                .mapToDouble(Progress::getCompletionPercentage)
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
                .mapToDouble(Progress::getCompletionPercentage)
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
        comparison.put("completionPercentile", calculatePercentile(studentProgress.getCompletionPercentage(),
                allProgress.stream().mapToDouble(Progress::getCompletionPercentage).toArray()));

        comparison.put("studentExamAverage", studentExamAverage);
        comparison.put("classExamAverage", classExamAverage);
        comparison.put("examPercentile", calculatePercentile(studentExamAverage,
                allSubmissions.stream().mapToDouble(Submission::getScore).toArray()));

        // Assignment comparison (instead of exercise)
        comparison.put("studentAssignmentAverage", studentAssignmentAverage);
        comparison.put("classAssignmentAverage", classAssignmentAverage);
        comparison.put("assignmentSubmissions", studentAssignmentSubmissions.size());
        comparison.put("assignmentPercentile", calculatePercentile(studentAssignmentAverage,
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

        // Calculate overall course metrics
        double averageCompletion = allProgress.stream()
                .mapToDouble(Progress::getCompletionPercentage)
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

        // Build performance data
        performance.put("totalStudents", course.getEnrolledStudents().size());
        performance.put("activeStudents", allProgress.size());
        performance.put("completedStudents", completedStudents);
        performance.put("averageCompletion", averageCompletion);
        performance.put("examsTaken", allSubmissions.size());
        performance.put("passedExams", passedExams);
        performance.put("averageExamScore", averageExamScore);
        performance.put("passRate", allSubmissions.isEmpty() ? 0 : (double) passedExams / allSubmissions.size() * 100);

        // Assignment metrics (instead of exercise metrics)
        performance.put("assignmentSubmissions", allAssignmentSubmissions.size());
        performance.put("averageAssignmentScore", averageAssignmentScore);
        performance.put("gradedAssignments", gradedAssignments);
        performance.put("assignmentGradingRate", allAssignmentSubmissions.isEmpty() ? 0 :
                (double) gradedAssignments / allAssignmentSubmissions.size() * 100);

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
            default:
                return "فعالیت";
        }
    }
    private String getContentTypeLabel(String activityType) {
        switch (activityType) {
            case "CONTENT_VIEW":
                return "ویدیوهای آموزشی";
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
        stats.put("loginTrend", calculateTrend(loginActivities, 30));

        stats.put("avgContentViews", contentViews.size() / 30);
        stats.put("viewTrend", calculateTrend(contentViews, 30));

        stats.put("avgExamSubmissions", examSubmissions.size() / 30);
        stats.put("examTrend", calculateTrend(examSubmissions, 30));

        stats.put("avgAssignmentSubmissions", assignmentSubmissions.size() / 30); // Changed from exercise
        stats.put("assignmentTrend", calculateTrend(assignmentSubmissions, 30)); // Changed from exercise

        return stats;
    }































    /**
     * Calculate study time for a specific course
     */
    public long calculateCourseStudyTime(User student, Course course) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusDays(90);

        List<ActivityLog> courseActivities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, threeMonthsAgo, LocalDateTime.now())
                .stream()
                .filter(log -> isStudyActivity(log.getActivityType()))
                .filter(log -> isCourseRelatedActivity(log, course.getId()))
                .collect(Collectors.toList());

        return courseActivities.stream()
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
    }

    /**
     * Calculate actual study time across multiple courses
     */
    public long calculateActualStudyTime(User student, List<Course> courses) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusDays(90);

        List<ActivityLog> studyActivities = activityLogRepository
                .findByUserAndTimestampBetweenOrderByTimestampDesc(student, threeMonthsAgo, LocalDateTime.now())
                .stream()
                .filter(log -> isStudyActivity(log.getActivityType()))
                .filter(log -> isCourseRelatedActivityForCourses(log, courses))
                .collect(Collectors.toList());

        return studyActivities.stream()
                .mapToLong(log -> log.getTimeSpent() != null ? log.getTimeSpent() : 0L)
                .sum();
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
    private boolean isAssignmentRelatedToCourse(Long assignmentId, Long courseId) {
        try {
            Optional<Assignment> assignmentOpt = assignmentRepository.findById(assignmentId);

            if (assignmentOpt.isPresent()) {
                Assignment assignment = assignmentOpt.get();
                return assignment.getLesson() != null &&
                        assignment.getLesson().getCourse() != null &&
                        assignment.getLesson().getCourse().getId().equals(courseId);
            }
            return false;
        } catch (Exception e) {
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
    public Map<String, List<Map<String, Object>>> getTopPerformers(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, List<Map<String, Object>>> topPerformers = new HashMap<>();

        // Get all progress records for this course
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Top by completion rate
        List<Map<String, Object>> topByCompletion = allProgress.stream()
                .sorted((p1, p2) -> Double.compare(p2.getCompletionPercentage(), p1.getCompletionPercentage()))
                .limit(5)
                .map(progress -> {
                    Map<String, Object> studentData = new HashMap<>();
                    User student = progress.getStudent();
                    studentData.put("studentId", student.getId());
                    studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
                    studentData.put("value", progress.getCompletionPercentage());
                    studentData.put("completedLessons", progress.getCompletedLessons().size());
                    studentData.put("totalLessons", progress.getTotalLessons());
                    return studentData;
                })
                .collect(Collectors.toList());

        // Top by exam scores
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        Map<Long, Double> studentExamAverages = allSubmissions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStudent().getId(),
                        Collectors.averagingDouble(Submission::getScore)
                ));

        List<Map<String, Object>> topByExamScores = studentExamAverages.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> studentData = new HashMap<>();
                    User student = userRepository.findById(entry.getKey())
                            .orElse(null);
                    if (student != null) {
                        studentData.put("studentId", student.getId());
                        studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
                        studentData.put("value", Math.round(entry.getValue() * 10.0) / 10.0);

                        // Count exams taken
                        long examsTaken = allSubmissions.stream()
                                .filter(s -> s.getStudent().getId().equals(student.getId()))
                                .count();
                        studentData.put("examsTaken", examsTaken);
                    }
                    return studentData;
                })
                .filter(data -> data.get("studentName") != null)
                .collect(Collectors.toList());

        // Top by assignment scores
        List<AssignmentSubmission> allAssignmentSubmissions = assignmentSubmissionRepository.findAll().stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                .filter(as -> as.getScore() != null)
                .collect(Collectors.toList());

        Map<Long, Double> studentAssignmentAverages = allAssignmentSubmissions.stream()
                .collect(Collectors.groupingBy(
                        as -> as.getStudent().getId(),
                        Collectors.averagingDouble(AssignmentSubmission::getScore)
                ));

        List<Map<String, Object>> topByAssignmentScores = studentAssignmentAverages.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> studentData = new HashMap<>();
                    User student = userRepository.findById(entry.getKey())
                            .orElse(null);
                    if (student != null) {
                        studentData.put("studentId", student.getId());
                        studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
                        studentData.put("value", Math.round(entry.getValue() * 10.0) / 10.0);

                        // Count assignments submitted
                        long assignmentsSubmitted = allAssignmentSubmissions.stream()
                                .filter(as -> as.getStudent().getId().equals(student.getId()))
                                .count();
                        studentData.put("assignmentsSubmitted", assignmentsSubmitted);
                    }
                    return studentData;
                })
                .filter(data -> data.get("studentName") != null)
                .collect(Collectors.toList());

        // Top by study time (from progress records)
        List<Map<String, Object>> topByStudyTime = allProgress.stream()
                .filter(p -> p.getTotalStudyTime() != null && p.getTotalStudyTime() > 0)
                .sorted((p1, p2) -> Long.compare(
                        p2.getTotalStudyTime() != null ? p2.getTotalStudyTime() : 0L,
                        p1.getTotalStudyTime() != null ? p1.getTotalStudyTime() : 0L))
                .limit(5)
                .map(progress -> {
                    Map<String, Object> studentData = new HashMap<>();
                    User student = progress.getStudent();
                    studentData.put("studentId", student.getId());
                    studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
                    studentData.put("value", Math.round(progress.getTotalStudyTime() * 10.0) / 10.0); // Convert to hours
                    studentData.put("totalseconds", progress.getTotalStudyTime());
                    return studentData;
                })
                .collect(Collectors.toList());

        topPerformers.put("completion", topByCompletion);
        topPerformers.put("examScores", topByExamScores);
        topPerformers.put("assignmentScores", topByAssignmentScores);
        topPerformers.put("studyTime", topByStudyTime);

        return topPerformers;
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
        LocalDateTime startDate = calculateStartDate(endDate, period);

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
                        // Check if student answered this question correctly
                        Long answerId = sub.getAnswers().get(question.getId());
                        if (answerId == null) return 1; // No answer = incorrect

                        return question.getAnswers().stream()
                                .filter(a -> a.getId().equals(answerId))
                                .findFirst()
                                .map(Answer::getCorrect)
                                .orElse(false) ? 0 : 1; // Correct = 0, Incorrect = 1
                    })
                    .sum();

            double errorRate = totalAnswers > 0 ? (double) incorrectAnswers / totalAnswers * 100 : 0;

            // Calculate average time spent on this question
            double averageTime = submissions.stream()
                    .mapToDouble(sub -> {
                        // Estimate time per question (total time / number of questions)
                        int questionCount = sub.getExam().getQuestions().size();
                        return questionCount > 0 ? (double) sub.getTimeSpent() / questionCount : 0;
                    })
                    .average()
                    .orElse(0.0);

            // Calculate difficulty score (based on error rate and time)
            double difficultyScore = Math.min(100, errorRate + (averageTime * 10));

            // Only include challenging questions (difficulty > 60%)
            if (difficultyScore > 60) {
                Map<String, Object> questionData = new HashMap<>();
                questionData.put("id", question.getId());
                questionData.put("title", "سوال " + question.getId());
                questionData.put("text", question.getText());
                questionData.put("difficultyScore", Math.round(difficultyScore));
                questionData.put("errorRate", Math.round(errorRate));
                questionData.put("averageTime", Math.round(averageTime));
                questionData.put("totalAnswers", totalAnswers);
                questionData.put("examTitle", question.getExam().getTitle());

                challengingQuestions.add(questionData);

                totalDifficult++;
                needsReview++;
            }

            totalDifficultyScore += difficultyScore;
            maxDifficulty = Math.max(maxDifficulty, difficultyScore);
        }

        // Sort by difficulty score descending
        challengingQuestions.sort((a, b) ->
                Integer.compare((Integer) b.get("difficultyScore"), (Integer) a.get("difficultyScore")));

        // Prepare statistics
        Map<String, Object> stats = new HashMap<>();
        stats.put("mostDifficult", Math.round(maxDifficulty));
        stats.put("averageDifficulty", courseQuestions.isEmpty() ? 0 : Math.round(totalDifficultyScore / courseQuestions.size()));
        stats.put("needsReview", needsReview);

        result.put("questions", challengingQuestions);
        result.put("stats", stats);

        return result;
    }

    /**
     * Get at-risk students for a specific course
     */
    public Map<String, Object> getAtRiskStudents(Long courseId, String period) {
        Map<String, Object> result = new HashMap<>();

        // Validate course exists
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Calculate time range
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(endDate, period);

        // Get all students enrolled in this course
        List<Progress> courseProgress = progressRepository.findByCourse(course);

        List<Map<String, Object>> atRiskStudents = new ArrayList<>();

        // Risk factor counters
        int lowAttendanceCount = 0;
        int poorPerformanceCount = 0;
        int inactivityCount = 0;
        int behavioralIssuesCount = 0;

        for (Progress progress : courseProgress) {
            User student = progress.getStudent();

            // Calculate risk factors
            Map<String, Object> factors = new HashMap<>();
            Map<String, Boolean> riskFlags = new HashMap<>();
            int riskLevel = 0;

            // 1. Check attendance rate
            List<ActivityLog> attendanceActivities = activityLogRepository
                    .findByUserAndActivityTypeAndTimestampBetween(
                            student, "LOGIN", startDate, endDate);

            double attendanceRate = Math.min(100, (attendanceActivities.size() * 100.0) / ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate()));
            factors.put("attendanceRate", Math.round(attendanceRate));

            if (attendanceRate < 60) {
                riskFlags.put("lowAttendance", true);
                riskLevel++;
                lowAttendanceCount++;
            } else {
                riskFlags.put("lowAttendance", false);
            }

            // 2. Check academic performance
            List<Submission> submissions = submissionRepository.findByStudentAndSubmissionTimeBetween(student, startDate, endDate);
            double averageScore = submissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            factors.put("averageScore", Math.round(averageScore));

            if (averageScore < 50) {
                riskFlags.put("poorPerformance", true);
                riskLevel++;
                poorPerformanceCount++;
            } else {
                riskFlags.put("poorPerformance", false);
            }

            // 3. Check activity level
            List<ActivityLog> recentActivities = activityLogRepository
                    .findByUserAndTimestampBetween(student, endDate.minusDays(7), endDate);

            long daysSinceLastActivity = recentActivities.isEmpty() ? 7 :
                    ChronoUnit.DAYS.between(
                            recentActivities.get(0).getTimestamp().toLocalDate(),
                            endDate.toLocalDate());

            factors.put("daysSinceLastActivity", daysSinceLastActivity);

            if (daysSinceLastActivity > 3) {
                riskFlags.put("inactivity", true);
                riskLevel++;
                inactivityCount++;
            } else {
                riskFlags.put("inactivity", false);
            }

            // 4. Check for behavioral issues (based on late submissions)
            List<AssignmentSubmission> lateSubmissions = assignmentSubmissionRepository
                    .findByStudentAndSubmittedAtBetween(student, startDate, endDate)
                    .stream()
                    .filter(sub -> sub.getSubmittedAt().isAfter(sub.getAssignment().getDueDate()))
                    .collect(Collectors.toList());

            if (lateSubmissions.size() > 2) {
                riskFlags.put("behavioralIssues", true);
                riskLevel++;
                behavioralIssuesCount++;
            } else {
                riskFlags.put("behavioralIssues", false);
            }

            // Only include students with at least one risk factor
            if (riskLevel > 0) {
                Map<String, Object> studentData = new HashMap<>();
                studentData.put("id", student.getId());
                studentData.put("firstName", student.getFirstName());
                studentData.put("lastName", student.getLastName());
                studentData.put("username", student.getUsername());
                studentData.put("email", student.getEmail());

                // Determine risk level
                String riskLevelText;
                if (riskLevel >= 3) {
                    riskLevelText = "high";
                } else if (riskLevel == 2) {
                    riskLevelText = "medium";
                } else {
                    riskLevelText = "low";
                }
                studentData.put("riskLevel", riskLevelText);
                studentData.put("factors", riskFlags);

                // Add detailed factor data
                factors.forEach(studentData::put);

                atRiskStudents.add(studentData);
            }
        }

        // Sort by risk level (high to low)
        atRiskStudents.sort((a, b) -> {
            String levelA = (String) a.get("riskLevel");
            String levelB = (String) b.get("riskLevel");
            Map<String, Integer> priority = Map.of("high", 3, "medium", 2, "low", 1);
            return priority.get(levelB).compareTo(priority.get(levelA));
        });

        // Prepare risk factors summary
        Map<String, Integer> riskFactors = new HashMap<>();
        riskFactors.put("lowAttendance", lowAttendanceCount);
        riskFactors.put("poorPerformance", poorPerformanceCount);
        riskFactors.put("inactivity", inactivityCount);
        riskFactors.put("behavioralIssues", behavioralIssuesCount);

        result.put("students", atRiskStudents);
        result.put("riskFactors", riskFactors);

        return result;
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
        LocalDateTime startDate = calculateStartDate(endDate, period);

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
}