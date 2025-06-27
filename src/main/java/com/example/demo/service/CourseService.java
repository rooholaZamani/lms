package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.ExerciseSubmissionRepository;
import com.example.demo.repository.ProgressRepository;
import com.example.demo.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final ExerciseSubmissionRepository exerciseSubmissionRepository;
    private final SubmissionRepository submissionRepository;

    public CourseService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository, ExerciseSubmissionRepository exerciseSubmissionRepository, SubmissionRepository submissionRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.exerciseSubmissionRepository = exerciseSubmissionRepository;
        this.submissionRepository = submissionRepository;
    }

    // اضافه کردن این متد
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    // متدهای موجود
    public Course createCourse(Course course, User teacher) {
        course.setTeacher(teacher);
        return courseRepository.save(course);
    }

    public List<Course> getTeacherCourses(User teacher) {
        return courseRepository.findByTeacher(teacher);
    }

    public List<Course> getEnrolledCourses(User student) {
        return courseRepository.findByEnrolledStudentsContaining(student);
    }

    public Course enrollStudent(Long courseId, User student) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getEnrolledStudents().contains(student)) {
            course.getEnrolledStudents().add(student);
            courseRepository.save(course);

            // Initialize progress tracking
            Progress progress = new Progress();
            progress.setStudent(student);
            progress.setCourse(course);
            progress.setLastAccessed(LocalDateTime.now());
            progress.setTotalLessons(course.getLessons().size());
            progress.setCompletedLessonCount(0);
            progress.setCompletionPercentage(0.0);
            progressRepository.save(progress);
        }

        return course;
    }

    public Course getCourseById(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
    }


    /**
     * دریافت لیست دانش‌آموزان دوره همراه با پیشرفت
     */
    public List<Map<String, Object>> getCourseStudentsWithProgress(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<Map<String, Object>> studentsData = new ArrayList<>();

        for (User student : course.getEnrolledStudents()) {
            Map<String, Object> studentData = new HashMap<>();

            // اطلاعات پایه دانش‌آموز
            studentData.put("id", student.getId());
            studentData.put("studentId", student.getId()); // برای سازگاری
            studentData.put("studentName", student.getFirstName() + " " + student.getLastName());
            studentData.put("firstName", student.getFirstName());
            studentData.put("lastName", student.getLastName());
            studentData.put("username", student.getUsername());
            studentData.put("email", student.getEmail());
            studentData.put("phoneNumber", student.getPhoneNumber());
            studentData.put("nationalId", student.getNationalId());

            // پیشرفت در دوره
            Optional<Progress> progressOpt = progressRepository.findByStudentAndCourse(student, course);
            if (progressOpt.isPresent()) {
                Progress progress = progressOpt.get();
                studentData.put("completionPercentage", progress.getCompletionPercentage());
                studentData.put("completedLessons", progress.getCompletedLessonCount());
                studentData.put("totalLessons", progress.getTotalLessons());
                studentData.put("lastAccessed", progress.getLastAccessed());
                studentData.put("totalStudyTime", progress.getTotalStudyTime());
                studentData.put("currentStreak", progress.getCurrentStreak());
            } else {
                // در صورت عدم وجود پیشرفت، مقادیر پیش‌فرض
                studentData.put("completionPercentage", 0.0);
                studentData.put("completedLessons", 0);
                studentData.put("totalLessons", course.getLessons().size());
                studentData.put("lastAccessed", null);
                studentData.put("totalStudyTime", 0L);
                studentData.put("currentStreak", 0);
            }

            // آمار آزمون‌ها
            List<Submission> examSubmissions = submissionRepository.findByStudent(student)
                    .stream()
                    .filter(s -> s.getExam().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            double averageExamScore = examSubmissions.stream()
                    .mapToDouble(Submission::getScore)
                    .average()
                    .orElse(0.0);

            studentData.put("examsTaken", examSubmissions.size());
            studentData.put("averageExamScore", Math.round(averageExamScore * 10.0) / 10.0);

            // آمار تمرین‌ها
            List<ExerciseSubmission> exerciseSubmissions = exerciseSubmissionRepository.findByStudent(student)
                    .stream()
                    .filter(s -> s.getExercise().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            studentData.put("exercisesDone", exerciseSubmissions.size());

            // وضعیت فعالیت
            boolean isActive = progressOpt.isPresent() &&
                    progressOpt.get().getLastAccessed() != null &&
                    progressOpt.get().getLastAccessed().isAfter(LocalDateTime.now().minusDays(7));
            studentData.put("isActive", isActive);

            studentsData.add(studentData);
        }

        // مرتب‌سازی بر اساس نام
        studentsData.sort((a, b) -> {
            String nameA = (String) a.get("studentName");
            String nameB = (String) b.get("studentName");
            return nameA.compareTo(nameB);
        });

        return studentsData;
    }
}