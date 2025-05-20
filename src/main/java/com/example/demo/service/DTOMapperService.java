package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.model.*;
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

    public LessonDTO mapToLessonDTO(Lesson lesson) {
        if (lesson == null) {
            return null;
        }

        LessonDTO dto = new LessonDTO();
        dto.setId(lesson.getId());
        dto.setTitle(lesson.getTitle());
        dto.setDescription(lesson.getDescription());
        dto.setOrderIndex(lesson.getOrderIndex());

        if (lesson.getCourse() != null) {
            dto.setCourseId(lesson.getCourse().getId());
            dto.setCourseTitle(lesson.getCourse().getTitle());
        }

        dto.setHasExam(lesson.getExam() != null);
        dto.setHasExercise(lesson.getExercise() != null);

        // Map content
        if (lesson.getContents() != null) {
            List<ContentDTO> contentDTOs = lesson.getContents().stream()
                    .map(this::mapToContentDTO)
                    .collect(Collectors.toList());
            dto.setContents(contentDTOs);
        }

        // Map exam if present
        if (lesson.getExam() != null) {
            dto.setExam(mapToExamDTO(lesson.getExam()));
        }

        // Map exercise if present
        if (lesson.getExercise() != null) {
            dto.setExercise(mapToExerciseDTO(lesson.getExercise()));
        }

        return dto;
    }

    public ContentDTO mapToContentDTO(Content content) {
        if (content == null) {
            return null;
        }

        ContentDTO dto = new ContentDTO();
        dto.setId(content.getId());
        dto.setTitle(content.getTitle());
        dto.setType(content.getType());
        dto.setTextContent(content.getTextContent());
        dto.setOrderIndex(content.getOrderIndex());

        if (content.getFile() != null) {
            dto.setFile(mapToFileMetadataDTO(content.getFile()));
        }

        return dto;
    }

    public FileMetadataDTO mapToFileMetadataDTO(FileMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        FileMetadataDTO dto = new FileMetadataDTO();
        dto.setId(metadata.getId());
        dto.setOriginalFilename(metadata.getOriginalFilename());
        dto.setContentType(metadata.getContentType());
        dto.setFileSize(metadata.getFileSize());

        return dto;
    }

    public ExamDTO mapToExamDTO(Exam exam) {
        if (exam == null) {
            return null;
        }

        ExamDTO dto = new ExamDTO();
        dto.setId(exam.getId());
        dto.setTitle(exam.getTitle());
        dto.setDescription(exam.getDescription());
        dto.setTimeLimit(exam.getTimeLimit());
        dto.setPassingScore(exam.getPassingScore());

        // Map questions if needed
        if (exam.getQuestions() != null) {
            List<QuestionDTO> questionDTOs = exam.getQuestions().stream()
                    .map(this::mapToQuestionDTO)
                    .collect(Collectors.toList());
            dto.setQuestions(questionDTOs);
        }

        return dto;
    }

    public ExerciseDTO mapToExerciseDTO(Exercise exercise) {
        if (exercise == null) {
            return null;
        }

        ExerciseDTO dto = new ExerciseDTO();
        dto.setId(exercise.getId());
        dto.setTitle(exercise.getTitle());
        dto.setDescription(exercise.getDescription());
        dto.setTimeLimit(exercise.getTimeLimit());
        dto.setPassingScore(exercise.getPassingScore());
        dto.setAdaptiveDifficulty(exercise.getAdaptiveDifficulty());

        return dto;
    }

    public QuestionDTO mapToQuestionDTO(Question question) {
        if (question == null) {
            return null;
        }

        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setPoints(question.getPoints());

        // Map answers
        if (question.getAnswers() != null) {
            List<AnswerDTO> answerDTOs = question.getAnswers().stream()
                    .map(this::mapToAnswerDTO)
                    .collect(Collectors.toList());
            dto.setAnswers(answerDTOs);
        }

        return dto;
    }

    public AnswerDTO mapToAnswerDTO(Answer answer) {
        if (answer == null) {
            return null;
        }

        AnswerDTO dto = new AnswerDTO();
        dto.setId(answer.getId());
        dto.setText(answer.getText());
        dto.setCorrect(answer.isCorrect());

        return dto;
    }
    public List<LessonDTO> mapToLessonDTOList(List<Lesson> lessons) {
        return lessons.stream()
                .map(this::mapToLessonDTO)
                .collect(Collectors.toList());
    }
    public ProgressDTO mapToProgressDTO(Progress progress) {
        if (progress == null) {
            return null;
        }

        ProgressDTO dto = new ProgressDTO();
        dto.setId(progress.getId());

        if (progress.getStudent() != null) {
            dto.setStudentId(progress.getStudent().getId());
            dto.setStudentName(progress.getStudent().getFirstName() + " " + progress.getStudent().getLastName());
        }

        if (progress.getCourse() != null) {
            dto.setCourseId(progress.getCourse().getId());
            dto.setCourseTitle(progress.getCourse().getTitle());
        }

        dto.setCompletedLessons(progress.getCompletedLessons());
        dto.setViewedContent(progress.getViewedContent());
        dto.setLastAccessed(progress.getLastAccessed());
        dto.setTotalLessons(progress.getTotalLessons());
        dto.setCompletedLessonCount(progress.getCompletedLessonCount());
        dto.setCompletionPercentage(progress.getCompletionPercentage());

        return dto;
    }

    public SubmissionDTO mapToSubmissionDTO(Submission submission) {
        if (submission == null) {
            return null;
        }

        SubmissionDTO dto = new SubmissionDTO();
        dto.setId(submission.getId());

        if (submission.getStudent() != null) {
            dto.setStudentId(submission.getStudent().getId());
            dto.setStudentName(submission.getStudent().getFirstName() + " " + submission.getStudent().getLastName());
        }

        if (submission.getExam() != null) {
            dto.setExamId(submission.getExam().getId());
            dto.setExamTitle(submission.getExam().getTitle());
        }

        dto.setSubmissionTime(submission.getSubmissionTime());
        dto.setScore(submission.getScore());
        dto.setPassed(submission.isPassed());
        dto.setAnswers(submission.getAnswers());

        return dto;
    }
}