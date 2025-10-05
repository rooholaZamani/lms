// src/main/java/com/example/demo/service/LessonService.java
package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final ExamRepository examRepository;
    private final AssignmentRepository assignmentRepository;
    private final ContentRepository contentRepository;
    private final FileStorageService fileStorageService;

    public LessonService(
            LessonRepository lessonRepository,
            CourseRepository courseRepository,
            SubmissionRepository submissionRepository,
            AssignmentSubmissionRepository assignmentSubmissionRepository,
            ExamRepository examRepository,
            AssignmentRepository assignmentRepository,
            ContentRepository contentRepository,
            FileStorageService fileStorageService) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentSubmissionRepository = assignmentSubmissionRepository;
        this.examRepository = examRepository;
        this.assignmentRepository = assignmentRepository;
        this.contentRepository = contentRepository;
        this.fileStorageService = fileStorageService;
    }


    public List<Lesson> getCourseLessons(Long courseId) {
        return lessonRepository.findByCourseIdOrderByOrderIndex(courseId);
    }

    public Lesson getLessonById(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
    }

    public Lesson updateLesson(Long lessonId, Lesson lessonDetails) {
        Lesson lesson = getLessonById(lessonId);

        lesson.setTitle(lessonDetails.getTitle());
        lesson.setDescription(lessonDetails.getDescription());
        if (lessonDetails.getOrderIndex() != null) {
            lesson.setOrderIndex(lessonDetails.getOrderIndex());
        }

        return lessonRepository.save(lesson);
    }
    public List<Lesson> getTeacherLessons(User teacher) {
        // Get all courses taught by this teacher
        List<Course> teacherCourses = courseRepository.findByTeacher(teacher);

        List<Lesson> allLessons = new ArrayList<>();

        // Get all lessons from all courses
        for (Course course : teacherCourses) {
            List<Lesson> courseLessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            allLessons.addAll(courseLessons);
        }

        return allLessons;
    }

    @Transactional
    public void deleteLesson(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // 1. حذف تمام ارسال‌های آزمون این درس (برای جلوگیری از خطای FK constraint)
        Optional<Exam> examOpt = examRepository.findByLessonId(lessonId);
        if (examOpt.isPresent()) {
            List<Submission> examSubmissions = submissionRepository.findByExam(examOpt.get());
            submissionRepository.deleteAll(examSubmissions);
        }

        // 2. حذف تمام ارسال‌های تکالیف این درس (برای جلوگیری از خطای FK constraint)
        List<Assignment> assignments = assignmentRepository.findByLesson(lesson);
        List<AssignmentSubmission> assignmentSubmissions = new ArrayList<>();
        for (Assignment assignment : assignments) {
            List<AssignmentSubmission> submissions = assignmentSubmissionRepository.findByAssignment(assignment);
            assignmentSubmissions.addAll(submissions);

            // حذف فایل‌های فیزیکی ارسال‌های تکالیف
            for (AssignmentSubmission submission : submissions) {
                if (submission.getFile() != null) {
                    try {
                        fileStorageService.deleteFile(submission.getFile());
                    } catch (Exception e) {
                        System.err.println("Could not delete submission file: " + e.getMessage());
                    }
                }
            }
        }
        assignmentSubmissionRepository.deleteAll(assignmentSubmissions);

        // 3. حذف فایل‌های فیزیکی محتوا و تکالیف
        List<Content> contents = contentRepository.findByLessonOrderByOrderIndex(lesson);
        for (Content content : contents) {
            if (content.getFile() != null) {
                try {
                    fileStorageService.deleteFile(content.getFile());
                } catch (Exception e) {
                    System.err.println("Could not delete content file: " + e.getMessage());
                }
            }
        }

        for (Assignment assignment : assignments) {
            if (assignment.getFile() != null) {
                try {
                    fileStorageService.deleteFile(assignment.getFile());
                } catch (Exception e) {
                    System.err.println("Could not delete assignment file: " + e.getMessage());
                }
            }
        }

        // 4. حذف درس (cascade به Exam، Question، Assignment، Content)
        lessonRepository.deleteById(lessonId);
    }

    @Transactional
    public Lesson createLesson(Lesson lesson, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Validate duration is provided
        if (lesson.getDuration() == null || lesson.getDuration() <= 0) {
            throw new RuntimeException("Lesson duration must be provided and greater than 0");
        }

        lesson.setCourse(course);

        // Set order index if not provided
        if (lesson.getOrderIndex() == null) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
            lesson.setOrderIndex(lessons.size());
        }

        return lessonRepository.save(lesson);
    }
}