package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final SubmissionRepository submissionRepository;
    private final ExerciseSubmissionRepository exerciseSubmissionRepository;
    private final LessonRepository lessonRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;

    public AnalyticsService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            ExerciseSubmissionRepository exerciseSubmissionRepository,
            LessonRepository lessonRepository,
            ExamRepository examRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository,
            ActivityLogRepository activityLogRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Get overall student performance across all courses
     */
    public Map<String, Object> getStudentPerformance(User student) {
        Map<String, Object> performance = new HashMap<>();

        // Get all progress records for the student
        List<Progress> progressList = progressRepository.findByStudent(student);

        // Get all exam submissions
        List<Submission> examSubmissions = submissionRepository.findByStudent(student);

        // Get all exercise submissions if they exist in the system
        List<ExerciseSubmission> exerciseSubmissions = exerciseSubmissionRepository.findByStudent(student);

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

        // Calculate average exercise score if applicable
        double averageExerciseScore = exerciseSubmissions.stream()
                .mapToDouble(ExerciseSubmission::getScore)
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

        // Prepare response
        performance.put("totalCourses", progressList.size());
        performance.put("completedCourses", completedCourses);
        performance.put("averageCompletion", averageCompletion);
        performance.put("examsTaken", examSubmissions.size());
        performance.put("passedExams", passedExams);
        performance.put("averageExamScore", averageExamScore);
        performance.put("exercisesTaken", exerciseSubmissions.size());
        performance.put("averageExerciseScore", averageExerciseScore);

        // Add time-based metrics like recent activity
        performance.put("recentActivity", getRecentActivity(student));

        return performance;
    }

    /**
     * Compare student performance against course average
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

        // Build comparison data
        comparison.put("studentCompletion", studentProgress.getCompletionPercentage());
        comparison.put("classAverageCompletion", averageCompletion);
        comparison.put("completionPercentile", calculatePercentile(studentProgress.getCompletionPercentage(),
                allProgress.stream().mapToDouble(Progress::getCompletionPercentage).toArray()));

        comparison.put("studentExamAverage", studentExamAverage);
        comparison.put("classExamAverage", classExamAverage);
        comparison.put("examPercentile", calculatePercentile(studentExamAverage,
                allSubmissions.stream().mapToDouble(Submission::getScore).toArray()));

        return comparison;
    }

    /**
     * Get top performing students in a course
     */
    public Map<String, List<Map<String, Object>>> getTopPerformers(Long courseId) {
        Map<String, List<Map<String, Object>>> topPerformers = new HashMap<>();

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Get all progress records for this course
        List<Progress> allProgress = progressRepository.findAll().stream()
                .filter(p -> p.getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Top students by completion percentage
        List<Map<String, Object>> topByCompletion = allProgress.stream()
                .sorted(Comparator.comparing(Progress::getCompletionPercentage).reversed())
                .limit(5)
                .map(p -> {
                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("studentId", p.getStudent().getId());
                    studentData.put("studentName", p.getStudent().getFirstName() + " " + p.getStudent().getLastName());
                    studentData.put("completionPercentage", p.getCompletionPercentage());
                    return studentData;
                })
                .collect(Collectors.toList());

        // Get all exam submissions for this course
        List<Submission> allSubmissions = submissionRepository.findAll().stream()
                .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        // Calculate average score per student
        Map<User, Double> studentScores = new HashMap<>();
        for (Submission submission : allSubmissions) {
            User student = submission.getStudent();
            studentScores.merge(student, (double) submission.getScore(), (oldValue, newValue) ->
                    (oldValue + newValue) / 2);
        }

        // Top students by exam scores
        List<Map<String, Object>> topByExams = studentScores.entrySet().stream()
                .sorted(Map.Entry.<User, Double>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> studentData = new HashMap<>();
                    studentData.put("studentId", entry.getKey().getId());
                    studentData.put("studentName", entry.getKey().getFirstName() + " " + entry.getKey().getLastName());
                    studentData.put("averageScore", entry.getValue());
                    return studentData;
                })
                .collect(Collectors.toList());

        topPerformers.put("topByCompletion", topByCompletion);
        topPerformers.put("topByExamScores", topByExams);

        return topPerformers;
    }

    /**
     * Get overall course performance metrics for teacher
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

        // Build performance data
        performance.put("totalStudents", course.getEnrolledStudents().size());
        performance.put("activeStudents", allProgress.size());
        performance.put("completedStudents", completedStudents);
        performance.put("averageCompletion", averageCompletion);
        performance.put("examsTaken", allSubmissions.size());
        performance.put("passedExams", passedExams);
        performance.put("averageExamScore", averageExamScore);
        performance.put("passRate", allSubmissions.isEmpty() ? 0 : (double) passedExams / allSubmissions.size() * 100);

        return performance;
    }

    /**
     * Identify difficult lessons based on completion rates and exam scores
     */
    public List<Map<String, Object>> getDifficultLessons(Long courseId) {
        List<Map<String, Object>> difficultLessons = new ArrayList<>();

        // Get all lessons for this course
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
     * Identify struggling students based on progress and exam scores
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

            // Get student's exam/exercise activity
            List<Submission> examSubmissions = submissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("examsTaken", examSubmissions.size());

            // Get exercise submissions if they exist
            List<ExerciseSubmission> exerciseSubmissions = exerciseSubmissionRepository.findByStudent(student).stream()
                    .filter(s -> s.getExercise().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("exercisesTaken", exerciseSubmissions.size());

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

    // NEW METHODS IMPLEMENTATION

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

        // Get exercise submissions
        List<ExerciseSubmission> exerciseSubmissions = exerciseSubmissionRepository.findByStudent(student).stream()
                .filter(s -> s.getExercise().getLesson().getCourse().getId().equals(courseId))
                .collect(Collectors.toList());

        analysis.put("exercisesTaken", exerciseSubmissions.size());

        // Calculate average time per lesson and exam
        long totalStudyTime = progress != null && progress.getTotalStudyTime() != null ? progress.getTotalStudyTime() : 0L;
        int completedLessons = progress != null ? progress.getCompletedLessons().size() : 0;

        analysis.put("averageTimePerLesson", completedLessons > 0 ? totalStudyTime / completedLessons : 0);
        analysis.put("averageTimePerExam", examSubmissions.isEmpty() ? 0 :
                examSubmissions.stream().mapToLong(s -> s.getTimeSpent() != null ? s.getTimeSpent() : 0L)
                        .average().orElse(0.0));

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

            // Add score if it's an exam or exercise submission
            if ("EXAM_SUBMISSION".equals(activity.getActivityType())) {
                Optional<Submission> submission = submissionRepository.findById(activity.getRelatedEntityId());
                submission.ifPresent(s -> activityData.put("score", s.getScore()));
            } else if ("EXERCISE_SUBMISSION".equals(activity.getActivityType())) {
                Optional<ExerciseSubmission> submission = exerciseSubmissionRepository.findById(activity.getRelatedEntityId());
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
            examData.put("timeSpent", submission.getTimeSpent() != null ? submission.getTimeSpent() : 0);
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

        // Calculate total study hours
        long totalHours = allProgress.stream()
                .mapToLong(p -> p.getTotalStudyTime() != null ? p.getTotalStudyTime() : 0L)
                .sum() / 60; // Convert minutes to hours

        overview.put("totalStudents", totalStudents);
        overview.put("totalCourses", teacherCourses.size());
        overview.put("averageCompletion", averageCompletion);
        overview.put("averageScore", averageScore);
        overview.put("totalHours", totalHours);
        overview.put("avgTimePerStudent", totalStudents > 0 ? (double) totalHours / totalStudents : 0);
        overview.put("totalExams", allSubmissions.size());

        // Count assignments (if assignment repository exists)
        overview.put("totalAssignments", 0); // Placeholder

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

            // Placeholder for discussion posts
            dayData.put("discussionPosts", 0);

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
                questionData.put("topic", "General"); // Placeholder

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

    /**
     * Get daily engagement statistics
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

        stats.put("avgDailyLogins", loginActivities.size() / 30);
        stats.put("loginTrend", calculateTrend(loginActivities, 30));

        stats.put("avgContentViews", contentViews.size() / 30);
        stats.put("viewTrend", calculateTrend(contentViews, 30));

        stats.put("avgExamSubmissions", examSubmissions.size() / 30);
        stats.put("examTrend", calculateTrend(examSubmissions, 30));

        stats.put("avgDiscussions", 0); // Placeholder
        stats.put("discussionTrend", 0); // Placeholder

        return stats;
    }

    // Helper methods
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

    private String generateActivityDescription(ActivityLog activity) {
        switch (activity.getActivityType()) {
            case "LESSON_COMPLETION":
                return "Completed lesson";
            case "EXAM_SUBMISSION":
                return "Submitted exam";
            case "CONTENT_VIEW":
                return "Viewed content";
            case "EXERCISE_SUBMISSION":
                return "Submitted exercise";
            case "LOGIN":
                return "Logged in";
            default:
                return activity.getActivityType();
        }
    }

    private String getContentTypeLabel(String activityType) {
        switch (activityType) {
            case "CONTENT_VIEW":
                return "ویدیوهای آموزشی";
            case "EXERCISE_SUBMISSION":
                return "تمرین‌های عملی";
            case "EXAM_SUBMISSION":
                return "آزمون‌ها";
            case "LESSON_COMPLETION":
                return "درس‌ها";
            default:
                return "سایر";
        }
    }

    private double calculateEfficiency(String activityType, double avgTime) {
        // Simple efficiency calculation based on activity type and time
        // This is a placeholder - you'd implement based on your business logic
        switch (activityType) {
            case "CONTENT_VIEW":
                return avgTime < 20 ? 95 : (avgTime < 40 ? 85 : 70);
            case "EXERCISE_SUBMISSION":
                return avgTime < 30 ? 90 : (avgTime < 60 ? 80 : 65);
            default:
                return 80.0;
        }
    }

    private String getDifficultyLabel(double avgTime) {
        if (avgTime < 20) return "آسان";
        if (avgTime < 45) return "متوسط";
        if (avgTime < 70) return "سخت";
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
        // Calculate engagement based on frequency and time spent
        if (activities.isEmpty()) return 0.0;

        double avgTimeSpent = activities.stream()
                .mapToLong(ActivityLog::getTimeSpent)
                .average()
                .orElse(0.0);

        // Higher time spent = higher engagement (up to a point)
        return Math.min(95.0, 50 + avgTimeSpent); // Simple formula
    }

    private double calculateAverageScoreForQuestions(List<Question> questions) {
        // Get all submissions for these questions and calculate average score
        return 75.0; // Placeholder
    }

    private double calculateAverageTimeForQuestions(List<Question> questions) {
        // Calculate average time spent on these questions
        return 3.5; // Placeholder - 3.5 minutes average
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
}