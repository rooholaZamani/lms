package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.util.AnalyticsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;

    public ScoreAnalyticsService(
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            ExamRepository examRepository,
            UserRepository userRepository,
            CourseRepository courseRepository,
            ProgressRepository progressRepository) {
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
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
}
