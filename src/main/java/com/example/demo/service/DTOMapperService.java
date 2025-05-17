package com.example.demo.service;

import com.example.demo.dto.CourseDTO;
import com.example.demo.dto.LessonSummaryDTO;
import com.example.demo.dto.UserSummaryDTO;
import com.example.demo.model.Course;
import com.example.demo.model.Lesson;
import com.example.demo.model.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DTOMapperService {

    public UserSummaryDTO mapToUserSummary(User user) {
        if (user == null) {
            return null;
        }

        UserSummaryDTO dto = new UserSummaryDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        return dto;
    }

    public LessonSummaryDTO mapToLessonSummary(Lesson lesson) {
        if (lesson == null) {
            return null;
        }

        LessonSummaryDTO dto = new LessonSummaryDTO();
        dto.setId(lesson.getId());
        dto.setTitle(lesson.getTitle());
        dto.setDescription(lesson.getDescription());
        dto.setOrderIndex(lesson.getOrderIndex());
        dto.setHasExam(lesson.getExam() != null);
        dto.setHasExercise(lesson.getExercise() != null);
        return dto;
    }

    public CourseDTO mapToCourseDTO(Course course) {
        if (course == null) {
            return null;
        }

        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());

        // Map teacher
        if (course.getTeacher() != null) {
            dto.setTeacher(mapToUserSummary(course.getTeacher()));
        }

        // Map lessons
        if (course.getLessons() != null) {
            List<LessonSummaryDTO> lessonDTOs = course.getLessons().stream()
                    .map(this::mapToLessonSummary)
                    .collect(Collectors.toList());
            dto.setLessons(lessonDTOs);
        }

        // Map enrolled students
        if (course.getEnrolledStudents() != null) {
            List<UserSummaryDTO> studentDTOs = course.getEnrolledStudents().stream()
                    .map(this::mapToUserSummary)
                    .collect(Collectors.toList());
            dto.setEnrolledStudents(studentDTOs);
        }

        return dto;
    }

    public List<CourseDTO> mapToCourseDTOList(List<Course> courses) {
        return courses.stream()
                .map(this::mapToCourseDTO)
                .collect(Collectors.toList());
    }
}