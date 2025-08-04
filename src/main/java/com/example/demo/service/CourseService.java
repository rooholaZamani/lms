package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final ContentRepository contentRepository;
    private final LessonRepository lessonRepository;
    private final FileStorageService fileStorageService;
    private final AssignmentRepository assignmentRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public CourseService(
            CourseRepository courseRepository,
            ProgressRepository progressRepository,
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            ContentRepository contentRepository,
            LessonRepository lessonRepository,
            FileStorageService fileStorageService,
            AssignmentRepository assignmentRepository,
            FileMetadataRepository fileMetadataRepository) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.contentRepository = contentRepository;
        this.lessonRepository = lessonRepository;
        this.fileStorageService = fileStorageService;
        this.assignmentRepository = assignmentRepository;
        this.fileMetadataRepository = fileMetadataRepository;
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
        return courseRepository.findByEnrolledStudentsContaining(student)
                .stream()
                .filter(Course::getActive)
                .collect(Collectors.toList());
    }
    public List<Course> getAllActiveCourses() {
        return courseRepository.findByActiveTrue();
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
            studentData.put("averageExamScore", Math.round(averageExamScore));

            // آمار تکالیف (تغییر از exercise به assignment)
            List<AssignmentSubmission> assignmentSubmissions = assignmentSubmissionRepository.findByStudent(student)
                    .stream()
                    .filter(as -> as.getAssignment().getLesson().getCourse().getId().equals(courseId))
                    .collect(Collectors.toList());

            double averageAssignmentScore = assignmentSubmissions.stream()
                    .filter(as -> as.getScore() != null)
                    .mapToInt(AssignmentSubmission::getScore)
                    .average()
                    .orElse(0.0);

            studentData.put("assignmentsDone", assignmentSubmissions.size()); // Changed from exercisesDone
            studentData.put("averageAssignmentScore", Math.round(averageAssignmentScore)); // New field

            // وضعیت فعالیت
            boolean isActive = progressOpt.isPresent() &&
                    progressOpt.get().getLastAccessed() != null &&
                    progressOpt.get().getLastAccessed().isAfter(LocalDateTime.now().minusDays(7));
            studentData.put("isActive", isActive);

            studentsData.add(studentData);
        }


        studentsData.sort((a, b) -> {
            String nameA = (String) a.get("studentName");
            String nameB = (String) b.get("studentName");
            return nameA.compareTo(nameB);
        });

        return studentsData;
    }
    public Course updateCourse(Long courseId, Course courseData) {
        Course existingCourse = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + courseId));

        if (courseData.getTitle() != null && !courseData.getTitle().trim().isEmpty()) {
            existingCourse.setTitle(courseData.getTitle().trim());
        }

        if (courseData.getDescription() != null) {
            existingCourse.setDescription(courseData.getDescription().trim());
        }

        if (courseData.getActive() != null) {
            existingCourse.setActive(courseData.getActive());
        }

        return courseRepository.save(existingCourse);
    }

    public List<Course> getActiveCourses() {
        return courseRepository.findByActiveTrue();
    }

    public List<Course> getTeacherActiveCourses(User teacher) {
        return courseRepository.findByTeacherAndActiveTrue(teacher);
    }

    @Transactional
    public void deleteCourse(Long courseId, User teacher) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Access denied: Only course owner can delete this course");
        }

        // 1. جمع‌آوری تمام رکوردهای FileMetadata برای حذف فیزیکی
        List<FileMetadata> filesToDelete = new ArrayList<>();
        for (Lesson lesson : course.getLessons()) {
            List<Content> contents = contentRepository.findByLessonOrderByOrderIndex(lesson);
            for (Content content : contents) {
                if (content.getFile() != null) {
                    filesToDelete.add(content.getFile());
                }
            }
            List<Assignment> assignments = assignmentRepository.findByLesson(lesson);
            for (Assignment assignment : assignments) {
                if (assignment.getFile() != null) {
                    filesToDelete.add(assignment.getFile());
                }
            }
        }

        // 2. حذف رکوردهای پیشرفت دانش‌آموزان
        List<Progress> courseProgresses = progressRepository.findByCourse(course);
        progressRepository.deleteAll(courseProgresses);

        // 3. حذف دوره که به صورت Cascade تمام دروس، محتواها و تکالیف را حذف می‌کند.
        // این کار باعث حذف رکوردهای FileMetadata از پایگاه داده نیز می‌شود.
        courseRepository.delete(course);

        // 4. حذف پوشه فیزیکی پس از اتمام تراکنش
        String courseDirectoryPath = String.format("courses/%d", courseId);
        fileStorageService.deleteDirectory(courseDirectoryPath);
    }
}