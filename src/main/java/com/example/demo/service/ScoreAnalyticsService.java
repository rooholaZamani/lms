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

/**
 * Service for score and grade analytics
 * Handles exam scores, grade distributions, and student performance metrics
 */
@Service
public class ScoreAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(ScoreAnalyticsService.class);

    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProgressService progressService;
    private final LessonRepository lessonRepository;
    private final AssignmentRepository assignmentRepository;

    public ScoreAnalyticsService(
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            ExamRepository examRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            CourseRepository courseRepository,
            ProgressRepository progressRepository,
            ActivityLogRepository activityLogRepository,
            ProgressService progressService,
            LessonRepository lessonRepository,
            AssignmentRepository assignmentRepository) {
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.activityLogRepository = activityLogRepository;
        this.progressService = progressService;
        this.lessonRepository = lessonRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Get exam scores for a course
     */
    public Map<String, Object> getCourseExamScores(Long courseId, String period, Long examId, boolean includeDetails) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Map<String, Object> result = new HashMap<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Get exam submissions for this course
        List<Submission> submissions;
        if (examId != null) {
            // Filter by specific exam
            submissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getExam() != null && s.getExam().getId().equals(examId))
                    .filter(s -> s.getSubmissionTime() != null)
                    .filter(s -> !s.getSubmissionTime().isBefore(startDate) && !s.getSubmissionTime().isAfter(endDate))
                    .collect(Collectors.toList());
        } else {
            // All exams in the course
            submissions = submissionRepository.findAll().stream()
                    .filter(s -> s.getExam() != null &&
                            s.getExam().getLesson() != null &&
                            s.getExam().getLesson().getCourse() != null &&
                            s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .filter(s -> s.getSubmissionTime() != null)
                    .filter(s -> !s.getSubmissionTime().isBefore(startDate) && !s.getSubmissionTime().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Calculate statistics
        List<Integer> scores = submissions.stream()
                .map(Submission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double averageScore = scores.isEmpty() ? 0 :
                scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int maxScore = scores.isEmpty() ? 0 :
                scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minScore = scores.isEmpty() ? 0 :
                scores.stream().mapToInt(Integer::intValue).min().orElse(0);

        result.put("courseId", courseId);
        result.put("courseName", course.getTitle());
        result.put("period", period);
        result.put("totalSubmissions", submissions.size());
        result.put("averageScore", AnalyticsUtils.roundTo2Decimals(averageScore));
        result.put("maxScore", maxScore);
        result.put("minScore", minScore);

        // Grade distribution
        Map<String, Integer> gradeDistribution = calculateExamGradeDistribution(submissions);
        result.put("gradeDistribution", gradeDistribution);

        if (includeDetails && examId != null) {
            // Include detailed student scores
            List<Map<String, Object>> studentScores = submissions.stream()
                    .map(submission -> {
                        Map<String, Object> scoreData = new HashMap<>();
                        scoreData.put("studentId", submission.getStudent().getId());
                        scoreData.put("studentName", submission.getStudent().getFirstName() + " " +
                                submission.getStudent().getLastName());
                        scoreData.put("score", submission.getScore());
                        scoreData.put("submittedAt", submission.getSubmissionTime());
                        return scoreData;
                    })
                    .sorted((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")))
                    .collect(Collectors.toList());

            result.put("studentScores", studentScores);
        }

        return result;
    }

    /**
     * Get exam performance for a specific student
     */
    public List<Map<String, Object>> getStudentExamPerformance(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<Submission> submissions = submissionRepository.findAll().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .filter(s -> s.getScore() != null)
                .collect(Collectors.toList());

        return submissions.stream()
                .map(submission -> {
                    Map<String, Object> examData = new HashMap<>();
                    Exam exam = submission.getExam();

                    examData.put("examId", exam.getId());
                    examData.put("examTitle", exam.getTitle());
                    examData.put("score", submission.getScore());
                    examData.put("submittedAt", submission.getSubmissionTime());

                    if (exam.getLesson() != null && exam.getLesson().getCourse() != null) {
                        examData.put("courseId", exam.getLesson().getCourse().getId());
                        examData.put("courseName", exam.getLesson().getCourse().getTitle());
                    }

                    return examData;
                })
                .sorted((a, b) -> ((LocalDateTime) b.get("submittedAt"))
                        .compareTo((LocalDateTime) a.get("submittedAt")))
                .collect(Collectors.toList());
    }

    /**
     * Get enhanced student grades distribution
     */
    public Map<String, Object> getEnhancedStudentGradesDistribution(Long studentId, Long courseId, String timeFilter) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, timeFilter);

        Map<String, Object> result = new HashMap<>();

        // Get exam submissions
        List<Submission> examSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .filter(s -> s.getScore() != null)
                .filter(s -> s.getSubmissionTime() != null)
                .filter(s -> !s.getSubmissionTime().isBefore(startDate) && !s.getSubmissionTime().isAfter(endDate))
                .filter(s -> courseId == null || (s.getExam() != null &&
                        s.getExam().getLesson() != null &&
                        s.getExam().getLesson().getCourse() != null &&
                        s.getExam().getLesson().getCourse().getId().equals(courseId)))
                .collect(Collectors.toList());

        // Get assignment submissions
        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findAll().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .filter(s -> s.getScore() != null)
                .filter(s -> s.getSubmittedAt() != null)
                .filter(s -> !s.getSubmittedAt().isBefore(startDate) && !s.getSubmittedAt().isAfter(endDate))
                .filter(s -> courseId == null || (s.getAssignment() != null &&
                        s.getAssignment().getLesson() != null &&
                        s.getAssignment().getLesson().getCourse() != null &&
                        s.getAssignment().getLesson().getCourse().getId().equals(courseId)))
                .collect(Collectors.toList());

        // Calculate enhanced distributions
        Map<String, Object> examDistribution = calculateEnhancedExamGradeDistribution(examSubmissions);
        Map<String, Object> assignmentDistribution = calculateEnhancedAssignmentGradeDistribution(assignmentSubmissions);

        result.put("examGrades", examDistribution);
        result.put("assignmentGrades", assignmentDistribution);
        result.put("totalExams", examSubmissions.size());
        result.put("totalAssignments", assignmentSubmissions.size());

        return result;
    }

    /**
     * Get student grades distribution (simple version)
     */
    public Map<String, Object> getStudentGradesDistribution(Long studentId, Long courseId, String timeFilter) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LocalDateTime endDate = AnalyticsUtils.getNowInIranTime();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, timeFilter);

        Map<String, Object> result = new HashMap<>();

        // Get exam submissions
        List<Submission> examSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getStudent().getId().equals(studentId))
                .filter(s -> s.getScore() != null)
                .filter(s -> s.getSubmissionTime() != null)
                .filter(s -> !s.getSubmissionTime().isBefore(startDate) && !s.getSubmissionTime().isAfter(endDate))
                .filter(s -> courseId == null || (s.getExam() != null &&
                        s.getExam().getLesson() != null &&
                        s.getExam().getLesson().getCourse() != null &&
                        s.getExam().getLesson().getCourse().getId().equals(courseId)))
                .collect(Collectors.toList());

        List<Integer> examScores = examSubmissions.stream()
                .map(Submission::getScore)
                .collect(Collectors.toList());

        Map<String, Integer> examGradeDistribution = calculateGradeDistribution(examScores, 100.0);

        result.put("examGrades", Map.of(
                "distribution", examGradeDistribution,
                "total", examSubmissions.size()
        ));

        return result;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate grade distribution from exam submissions
     */
    private Map<String, Integer> calculateExamGradeDistribution(List<Submission> examSubmissions) {
        List<Integer> scores = examSubmissions.stream()
                .map(Submission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return calculateGradeDistribution(scores, 100.0);
    }

    /**
     * Calculate grade distribution from assignment submissions
     */
    private Map<String, Integer> calculateAssignmentGradeDistribution(List<AssignmentSubmission> assignmentSubmissions) {
        List<Integer> grades = assignmentSubmissions.stream()
                .map(AssignmentSubmission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return calculateGradeDistribution(grades, 20.0);
    }

    /**
     * Calculate grade distribution with percentage-based categories
     */
    private Map<String, Integer> calculateGradeDistribution(List<Integer> scores, double maxScore) {
        Map<String, Integer> distribution = new HashMap<>();

        // Initialize all categories
        for (GradeCategory category : GradeCategory.values()) {
            distribution.put(category.name().toLowerCase(), 0);
        }

        // Categorize scores
        for (Integer score : scores) {
            if (score == null) continue;

            double percentage = (score / maxScore) * 100;
            GradeCategory category = GradeCategory.fromPercentage(percentage);
            String categoryName = category.name().toLowerCase();
            distribution.put(categoryName, distribution.get(categoryName) + 1);
        }

        return distribution;
    }

    /**
     * Calculate enhanced exam grade distribution with details
     */
    private Map<String, Object> calculateEnhancedExamGradeDistribution(List<Submission> examSubmissions) {
        Map<String, Object> result = new HashMap<>();

        if (examSubmissions.isEmpty()) {
            result.put("distribution", new HashMap<String, Integer>());
            result.put("averageScore", 0.0);
            result.put("maxScore", 0);
            result.put("minScore", 0);
            return result;
        }

        List<Integer> scores = examSubmissions.stream()
                .map(Submission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Integer> distribution = calculateGradeDistribution(scores, 100.0);

        double averageScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int maxScore = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minScore = scores.stream().mapToInt(Integer::intValue).min().orElse(0);

        result.put("distribution", distribution);
        result.put("averageScore", AnalyticsUtils.roundTo2Decimals(averageScore));
        result.put("maxScore", maxScore);
        result.put("minScore", minScore);

        return result;
    }

    /**
     * Calculate enhanced assignment grade distribution with details
     */
    private Map<String, Object> calculateEnhancedAssignmentGradeDistribution(List<AssignmentSubmission> assignmentSubmissions) {
        Map<String, Object> result = new HashMap<>();

        if (assignmentSubmissions.isEmpty()) {
            result.put("distribution", new HashMap<String, Integer>());
            result.put("averageGrade", 0.0);
            result.put("maxGrade", 0);
            result.put("minGrade", 0);
            return result;
        }

        List<Integer> grades = assignmentSubmissions.stream()
                .map(AssignmentSubmission::getScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Integer> distribution = calculateGradeDistribution(grades, 20.0);

        double averageGrade = grades.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int maxGrade = grades.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minGrade = grades.stream().mapToInt(Integer::intValue).min().orElse(0);

        result.put("distribution", distribution);
        result.put("averageGrade", AnalyticsUtils.roundTo2Decimals(averageGrade));
        result.put("maxGrade", maxGrade);
        result.put("minGrade", minGrade);

        return result;
    }

    /**
     * Get detailed exam analytics for a student
     * Includes question-by-question analysis and comparison with class performance
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
        Map<Long, Long> submissionAnswers = getSubmissionAnswers(submission);

        for (Question question : questions) {
            Map<String, Object> questionData = new HashMap<>();

            questionData.put("questionId", question.getId());
            questionData.put("questionText", question.getText());
            questionData.put("points", question.getPoints());

            // Get student's answer
            Long answerId = submissionAnswers.get(question.getId());

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

        // Optimized: Use repository method instead of stream filter
        long passedSubmissions = submissionRepository.countByExamAndPassedTrue(exam);
        long totalSubmissions = allSubmissions.size();

        double classPassRate = totalSubmissions > 0 ?
                (double) passedSubmissions / totalSubmissions * 100 : 0;

        examDetails.put("classAverageScore", classAverageScore);
        examDetails.put("classPassRate", classPassRate);
        examDetails.put("percentile", AnalyticsUtils.calculatePercentile(submission.getScore(),
                allSubmissions.stream().mapToDouble(Submission::getScore).toArray()));

        return examDetails;
    }

    /**
     * Get challenging questions for a teacher.
     * A question is considered challenging if its correct rate is below 70%.
     *
     * @param teacher The teacher user
     * @return List of challenging questions with their metrics
     */
    public List<Map<String, Object>> getChallengingQuestions(User teacher) {
        List<Question> teacherQuestions = questionRepository.findByTeacher(teacher);
        List<Map<String, Object>> challengingQuestions = new ArrayList<>();

        for (Question question : teacherQuestions) {
            // Get all submissions for this question
            List<Submission> submissions = submissionRepository.findAll().stream()
                    .filter(s -> {
                        Map<Long, Long> answers = getSubmissionAnswers(s);
                        return answers.containsKey(question.getId());
                    })
                    .collect(Collectors.toList());

            if (submissions.isEmpty()) continue;

            // Calculate correct rate
            long correctAnswers = submissions.stream()
                    .filter(s -> {
                        Map<Long, Long> answers = getSubmissionAnswers(s);
                        Long answerId = answers.get(question.getId());
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
                        .mapToLong(s -> s.getTimeSpent() != null ? s.getTimeSpent() / submissions.size() : 0)
                        .average()
                        .orElse(0.0);

                questionData.put("avgTimeSeconds", avgTime);

                challengingQuestions.add(questionData);
            }
        }

        // Sort by difficulty (descending)
        challengingQuestions.sort((q1, q2) -> {
            Double difficulty1 = (Double) q1.get("difficulty");
            Double difficulty2 = (Double) q2.get("difficulty");
            return difficulty2.compareTo(difficulty1);
        });

        return challengingQuestions;
    }

    /**
     * Get at-risk students for a course based on performance metrics.
     * Students are considered at-risk if their risk score >= 50.
     * Risk score is calculated with weighted factors:
     * - Progress rate vs course average: 50% weight
     * - Grade average vs course average: 35% weight
     * - Attendance days vs course average: 15% weight
     *
     * @param courseId Course ID
     * @param period Time period for analysis (day, week, month, etc.)
     * @return Map containing at-risk students and course statistics
     */
    public Map<String, Object> getAtRiskStudents(Long courseId, String period) {
        Map<String, Object> result = new HashMap<>();

        // Validate course exists
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Calculate time range
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = AnalyticsUtils.calculateStartDate(endDate, period);

        // Get all students enrolled in this course
        List<Progress> courseProgress = progressRepository.findByCourse(course);
        List<Map<String, Object>> atRiskStudents = new ArrayList<>();
        List<Map<String, Object>> allStudents = new ArrayList<>();

        // Calculate course-wide averages first
        Map<String, Double> courseAverages = calculateCourseAverages(course, startDate, endDate);

        // Process each student
        for (Progress progress : courseProgress) {
            User student = progress.getStudent();

            // Calculate individual student metrics
            Map<String, Double> studentMetrics = calculateStudentMetrics(student, course, progress, startDate, endDate);

            // Calculate weighted risk score
            Map<String, Object> riskAssessment = calculateWeightedRiskScore(studentMetrics, courseAverages);

            // Create student data object
            Map<String, Object> studentData = new HashMap<>();
            studentData.put("id", student.getId());
            studentData.put("firstName", student.getFirstName());
            studentData.put("lastName", student.getLastName());
            studentData.put("username", student.getUsername());
            studentData.put("email", student.getEmail());
            studentData.put("riskScore", riskAssessment.get("riskScore"));
            studentData.put("riskLevel", riskAssessment.get("riskLevel"));
            studentData.put("factors", riskAssessment.get("factors"));
            studentData.put("studentMetrics", studentMetrics);
            studentData.put("courseAverages", courseAverages);

            allStudents.add(studentData);

            // Include in at-risk list if score >= 50
            double riskScore = (Double) riskAssessment.get("riskScore");
            if (riskScore >= 50.0) {
                atRiskStudents.add(studentData);
            }
        }

        // Sort at-risk students by risk score (highest first)
        atRiskStudents.sort((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")));

        // Calculate course statistics
        Map<String, Object> courseStats = new HashMap<>();
        courseStats.put("totalStudents", allStudents.size());
        courseStats.put("atRiskCount", atRiskStudents.size());

        double averageRiskScore = allStudents.stream()
            .mapToDouble(s -> (Double) s.get("riskScore"))
            .average()
            .orElse(0.0);
        courseStats.put("averageRiskScore", Math.round(averageRiskScore * 10.0) / 10.0);

        // Count risk levels
        Map<String, Long> riskLevelCounts = atRiskStudents.stream()
            .collect(Collectors.groupingBy(
                s -> (String) s.get("riskLevel"),
                Collectors.counting()
            ));

        result.put("students", atRiskStudents);
        result.put("courseStats", courseStats);
        result.put("riskLevelCounts", riskLevelCounts);
        result.put("courseAverages", courseAverages);

        return result;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate course-wide averages for progress, grades, and attendance
     */
    private Map<String, Double> calculateCourseAverages(Course course, LocalDateTime startDate, LocalDateTime endDate) {
        List<Progress> courseProgress = progressRepository.findByCourse(course);
        Map<String, Double> averages = new HashMap<>();

        double totalProgress = 0.0;
        double totalGrade = 0.0;
        double totalAttendance = 0.0;
        int validProgressCount = 0;
        int validGradeCount = 0;
        int validAttendanceCount = 0;

        for (Progress progress : courseProgress) {
            User student = progress.getStudent();

            // Calculate progress percentage using granular activity-based calculation
            double progressPercentage = progressService.calculateProgressFromActivities(student, course);
            if (progressPercentage >= 0) {
                totalProgress += progressPercentage;
                validProgressCount++;
            }

            // Calculate average grade from all exams and assignments
            List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
                .collect(Collectors.toList());

            List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
                .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
                .filter(as -> as.isGraded() && as.getScore() != null)
                .collect(Collectors.toList());

            if (!examSubmissions.isEmpty() || !assignmentSubmissions.isEmpty()) {
                double examAvg = examSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

                double assignmentAvg = assignmentSubmissions.stream()
                    .mapToDouble(AssignmentSubmission::getScore)
                    .average()
                    .orElse(0.0);

                double combinedAvg = (!examSubmissions.isEmpty() && !assignmentSubmissions.isEmpty())
                    ? (examAvg + assignmentAvg) / 2.0
                    : (!examSubmissions.isEmpty() ? examAvg : assignmentAvg);

                if (combinedAvg > 0) {
                    totalGrade += combinedAvg;
                    validGradeCount++;
                }
            }

            // Calculate attendance days (unique activity days)
            Set<LocalDate> activeDays = activityLogRepository
                .findByUserAndTimestampBetween(student, startDate, endDate)
                .stream()
                .map(log -> log.getTimestamp().toLocalDate())
                .collect(Collectors.toSet());

            int attendanceDays = activeDays.size();
            totalAttendance += attendanceDays;
            validAttendanceCount++;
        }

        averages.put("avgProgress", validProgressCount > 0 ? totalProgress / validProgressCount : 0.0);
        averages.put("avgGrade", validGradeCount > 0 ? totalGrade / validGradeCount : 0.0);
        averages.put("avgAttendance", validAttendanceCount > 0 ? totalAttendance / validAttendanceCount : 0.0);

        return averages;
    }

    /**
     * Calculate individual student metrics
     */
    private Map<String, Double> calculateStudentMetrics(User student, Course course, Progress progress, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Double> metrics = new HashMap<>();

        // 1. Progress percentage using granular activity-based calculation
        double progressPercentage = progressService.calculateProgressFromActivities(student, course);
        metrics.put("progress", Math.max(0.0, progressPercentage));

        // 2. Average grade from exams and assignments
        List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
            .filter(s -> s.getExam().getLesson().getCourse().getId().equals(course.getId()))
            .collect(Collectors.toList());

        List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student).stream()
            .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(course.getId()))
            .filter(as -> as.isGraded() && as.getScore() != null)
            .collect(Collectors.toList());

        double examAvg = examSubmissions.stream()
            .mapToDouble(Submission::getScore)
            .average()
            .orElse(0.0);

        double assignmentAvg = assignmentSubmissions.stream()
            .mapToDouble(AssignmentSubmission::getScore)
            .average()
            .orElse(0.0);

        double combinedAvg = (!examSubmissions.isEmpty() && !assignmentSubmissions.isEmpty())
            ? (examAvg + assignmentAvg) / 2.0
            : (!examSubmissions.isEmpty() ? examAvg : assignmentAvg);

        metrics.put("averageGrade", Math.max(0.0, combinedAvg));

        // 3. Attendance days (unique activity days)
        Set<LocalDate> activeDays = activityLogRepository
            .findByUserAndTimestampBetween(student, startDate, endDate)
            .stream()
            .map(log -> log.getTimestamp().toLocalDate())
            .collect(Collectors.toSet());

        metrics.put("attendanceDays", (double) activeDays.size());

        return metrics;
    }

    /**
     * Calculate weighted risk score based on user's criteria:
     * - Progress rate vs course average: 50% weight
     * - Grade average vs course average: 35% weight
     * - Attendance days vs course average: 15% weight
     * Risk threshold: >= 50 points
     */
    private Map<String, Object> calculateWeightedRiskScore(Map<String, Double> studentMetrics, Map<String, Double> courseAverages) {
        Map<String, Object> assessment = new HashMap<>();
        Map<String, Double> factors = new HashMap<>();

        double progressFactor = 0.0;
        double gradeFactor = 0.0;
        double attendanceFactor = 0.0;

        // 1. Progress factor (50% weight)
        double studentProgress = studentMetrics.get("progress");
        double avgProgress = courseAverages.get("avgProgress");
        if (avgProgress > 0 && studentProgress < avgProgress) {
            progressFactor = Math.min(50.0, ((avgProgress - studentProgress) / avgProgress) * 50.0);
        }

        // 2. Grade factor (35% weight)
        double studentGrade = studentMetrics.get("averageGrade");
        double avgGrade = courseAverages.get("avgGrade");
        if (avgGrade > 0 && studentGrade < avgGrade) {
            gradeFactor = Math.min(35.0, ((avgGrade - studentGrade) / avgGrade) * 35.0);
        }

        // 3. Attendance factor (15% weight)
        double studentAttendance = studentMetrics.get("attendanceDays");
        double avgAttendance = courseAverages.get("avgAttendance");
        if (avgAttendance > 0 && studentAttendance < avgAttendance) {
            attendanceFactor = Math.min(15.0, ((avgAttendance - studentAttendance) / avgAttendance) * 15.0);
        }

        // Calculate total risk score
        double totalRiskScore = progressFactor + gradeFactor + attendanceFactor;

        // Determine risk level
        String riskLevel;
        if (totalRiskScore >= 75.0) {
            riskLevel = "HIGH";
        } else if (totalRiskScore >= 50.0) {
            riskLevel = "MEDIUM";
        } else if (totalRiskScore >= 25.0) {
            riskLevel = "LOW";
        } else {
            riskLevel = "NONE";
        }

        factors.put("progressFactor", Math.round(progressFactor * 10.0) / 10.0);
        factors.put("gradeFactor", Math.round(gradeFactor * 10.0) / 10.0);
        factors.put("attendanceFactor", Math.round(attendanceFactor * 10.0) / 10.0);

        assessment.put("riskScore", Math.round(totalRiskScore * 10.0) / 10.0);
        assessment.put("riskLevel", riskLevel);
        assessment.put("factors", factors);

        return assessment;
    }

    /**
     * Get top performers in a course across multiple metrics:
     * - Top 5 by completion rate
     * - Top 5 by exam average scores
     * - Top 5 by assignment average scores
     * - Top 5 by study time
     *
     * @param courseId Course ID
     * @return Map containing lists of top performers for each metric
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
                        studentData.put("value", Math.round(entry.getValue() * 10.0));

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
                        studentData.put("value", Math.round(entry.getValue() * 10.0));

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
                    studentData.put("value", Math.round(progress.getTotalStudyTime() / 60.0)); // Convert seconds to minutes for display
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
     * Get difficult lessons in a course based on completion rates, exam pass rates, and assignment scores.
     * Higher difficulty score indicates more challenging lessons for students.
     *
     * @param courseId Course ID
     * @return List of lessons with difficulty metrics, sorted by difficulty (descending)
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

                // Optimized: Use repository method instead of stream().filter()
                long passedCount = submissionRepository.countByExamAndPassedTrue(exam);
                double passRate = examSubmissions.isEmpty() ? 0 :
                        (double) passedCount / examSubmissions.size() * 100;

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

                // Optimized: Use repository method instead of stream().filter()
                long passedCount = submissionRepository.countByExamAndPassedTrue(exam);
                double passRate = examSubmissions.isEmpty() ? 0 :
                        (double) passedCount / examSubmissions.size() * 100;

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
     * Identify struggling students based on progress and scores.
     * Students with lower completion rates and exam/assignment scores receive higher struggle scores.
     *
     * @param courseId Course ID
     * @return List of students with struggle metrics, sorted by struggle score (descending)
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

    // ==================== Private Helper Methods ====================

    /**
     * Parse submission answers from JSON string
     * Returns a map of questionId -> answerId
     */
    private Map<Long, Long> getSubmissionAnswers(Submission submission) {
        Map<Long, Long> answers = new HashMap<>();

        if (submission.getAnswersJson() == null || submission.getAnswersJson().trim().isEmpty()) {
            return answers;
        }

        try {
            // Simple JSON parsing for backward compatibility
            String answersJson = submission.getAnswersJson();
            if (answersJson.startsWith("{") && answersJson.endsWith("}")) {
                String content = answersJson.substring(1, answersJson.length() - 1);

                if (!content.trim().isEmpty()) {
                    String[] pairs = content.split(",");

                    for (String pair : pairs) {
                        String[] keyValue = pair.split(":", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim().replaceAll("\"", "");
                            String value = keyValue[1].trim().replaceAll("\"", "");

                            try {
                                // Only include simple numeric answers for analytics
                                Long questionId = Long.parseLong(key);
                                Long answerId = Long.parseLong(value);
                                answers.put(questionId, answerId);
                            } catch (NumberFormatException e) {
                                // Skip complex answers that can't be converted to Long
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map if parsing fails
        }

        return answers;
    }
}
