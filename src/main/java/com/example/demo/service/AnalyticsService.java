package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;

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

    public AnalyticsService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            ExerciseSubmissionRepository exerciseSubmissionRepository,
            LessonRepository lessonRepository,
            ExamRepository examRepository,
            QuestionRepository questionRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
        this.lessonRepository = lessonRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
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
        // FIX: Added explicit casting for the comparator
        difficultLessons.sort((d1, d2) -> {
            Double score1 = (Double) ((Map<String, Object>) d1).get("difficultyScore");
            Double score2 = (Double) ((Map<String, Object>) d2).get("difficultyScore");
            return score2.compareTo(score1); // Reversed order
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
        // FIX: Added explicit casting for the comparator
        strugglingStudents.sort((s1, s2) -> {
            Double score1 = (Double) ((Map<String, Object>) s1).get("struggleScore");
            Double score2 = (Double) ((Map<String, Object>) s2).get("struggleScore");
            return score2.compareTo(score1); // Reversed order
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
        // FIX: Added explicit casting for the comparator
        participationMetrics.sort((p1, p2) -> {
            Double rate1 = (Double) ((Map<String, Object>) p1).get("participationRate");
            Double rate2 = (Double) ((Map<String, Object>) p2).get("participationRate");
            return rate2.compareTo(rate1); // Reversed order
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
                    questionData.put("correct", answer.isCorrect());
                    questionData.put("pointsEarned", answer.isCorrect() ? question.getPoints() : 0);
                }
            } else {
                questionData.put("studentAnswer", "Not answered");
                questionData.put("correct", false);
                questionData.put("pointsEarned", 0);
            }

            // Add correct answer for reference
            Optional<Answer> correctAnswerOpt = question.getAnswers().stream()
                    .filter(Answer::IsCorrect)
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

    // Helper methods

    private List<Map<String, Object>> getRecentActivity(User student) {
        // This would return recent activities like completed lessons, taken exams, etc.
        // Simplified implementation for demo purposes
        List<Map<String, Object>> recentActivities = new ArrayList<>();

        // Add a few sample activities
        Map<String, Object> activity1 = new HashMap<>();
        activity1.put("type", "LESSON_COMPLETION");
        activity1.put("timestamp", new Date());
        activity1.put("description", "Completed a lesson");

        Map<String, Object> activity2 = new HashMap<>();
        activity2.put("type", "EXAM_SUBMISSION");
        activity2.put("timestamp", new Date());
        activity2.put("description", "Took an exam");

        recentActivities.add(activity1);
        recentActivities.add(activity2);

        return recentActivities;
    }

    private double calculatePercentile(double value, double[] values) {
        // Calculate what percentile the value falls into
        if (values.length == 0) {
            return 0;
        }

        int count = 0;
        for (double v : values) {
            if (v < value) {
                count++;
            }
        }

        return (double) count / values.length * 100;
    }
}