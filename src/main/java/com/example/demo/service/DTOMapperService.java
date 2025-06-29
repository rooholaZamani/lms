package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.AssignmentRepository;
import com.example.demo.repository.SubmissionRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DTOMapperService {
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;

    public DTOMapperService(SubmissionRepository submissionRepository, AssignmentRepository assignmentRepository) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
    }

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
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setNationalId(user.getNationalId());
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

        // Check if lesson has assignments
        List<Assignment> assignments = assignmentRepository.findByLessonId(lesson.getId());
        dto.setHasAssignment(!assignments.isEmpty());

        return dto;
    }

    public CourseDTO mapToCourseDTO(Course course, boolean includeDetails) {
        if (course == null) {
            return null;
        }

        CourseDTO dto = new CourseDTO();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());

        // Map teacher (always included)
        if (course.getTeacher() != null) {
            dto.setTeacher(mapToUserSummary(course.getTeacher()));
        }

        if (includeDetails) {
            // Full details mapping
            dto.setPrerequisite(course.getPrerequisite());
            dto.setTotalDuration(course.getTotalDuration());

            // Map lessons with full details
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
        } else {
            // Summary mapping (for list views)
            if (course.getLessons() != null) {
                List<LessonSummaryDTO> lessonSummaries = course.getLessons().stream()
                        .map(lesson -> {
                            LessonSummaryDTO summary = new LessonSummaryDTO();
                            summary.setId(lesson.getId());
                            summary.setTitle(lesson.getTitle());
                            summary.setOrderIndex(lesson.getOrderIndex());
                            summary.setHasExam(lesson.getExam() != null);

                            // Check assignments for this lesson
                            List<Assignment> assignments = assignmentRepository.findByLessonId(lesson.getId());
                            summary.setHasAssignment(!assignments.isEmpty());

                            return summary;
                        })
                        .collect(Collectors.toList());
                dto.setLessons(lessonSummaries);
            }

            // Don't include enrolled students for summary view
            dto.setEnrolledStudents(new ArrayList<>());
        }

        return dto;
    }

    // Add convenience method for backward compatibility
    public CourseDTO mapToCourseDTO(Course course) {
        return mapToCourseDTO(course, true); // Default to full details
    }

    // UPDATE the list mapping methods:
    public List<CourseDTO> mapToCourseDTOList(List<Course> courses) {
        return courses.stream()
                .map(course -> mapToCourseDTO(course, true)) // Full details
                .collect(Collectors.toList());
    }

    public List<CourseDTO> mapToCourseDTOListSummary(List<Course> courses) {
        return courses.stream()
                .map(course -> mapToCourseDTO(course, false)) // Summary only
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
        dto.setDuration(lesson.getDuration());
        dto.setCreatedAt(lesson.getCreatedAt());

        if (lesson.getCourse() != null) {
            dto.setCourseId(lesson.getCourse().getId());
            dto.setCourseTitle(lesson.getCourse().getTitle());

            // Add course object
            LessonDTO.CourseSummaryDTO courseSummary = new LessonDTO.CourseSummaryDTO();
            courseSummary.setId(lesson.getCourse().getId());
            courseSummary.setTitle(lesson.getCourse().getTitle());
            dto.setCourse(courseSummary);
        }

        dto.setHasExam(lesson.getExam() != null);

        // Check if lesson has assignments
        List<Assignment> assignments = assignmentRepository.findByLessonId(lesson.getId());
        dto.setHasAssignment(!assignments.isEmpty());

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

    public ExamDTO mapToExamDTO(Exam exam, User currentStudent) {
        ExamDTO dto = mapToExamDTO(exam); // Previous method

        // **Added**: If student is provided, check their participation status
        if (currentStudent != null) {
            Optional<Submission> submission = submissionRepository.findByStudentAndExam(currentStudent, exam);
            if (submission.isPresent()) {
                Submission sub = submission.get();
                dto.setHasStudentTaken(true);
                dto.setStudentScore(sub.getScore());
                dto.setStudentPassed(sub.isPassed());
                dto.setStudentSubmissionTime(sub.getSubmissionTime());
            } else {
                dto.setHasStudentTaken(false);
            }
        }

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

        // New finalization fields
        if (exam.getStatus() != null) {
            dto.setStatus(exam.getStatus().toString());
        }
        dto.setFinalizedAt(exam.getFinalizedAt());

        // Safe handling of finalizer name
        if (exam.getFinalizedBy() != null) {
            String finalizerName = exam.getFinalizedBy().getFirstName();
            if (exam.getFinalizedBy().getLastName() != null) {
                finalizerName += " " + exam.getFinalizedBy().getLastName();
            }
            dto.setFinalizedBy(finalizerName);
        }

        dto.setTotalPossibleScore(exam.getTotalPossibleScore());
        dto.setAvailableFrom(exam.getAvailableFrom());
        dto.setAvailableTo(exam.getAvailableTo());

        // Lesson information
        if (exam.getLesson() != null) {
            dto.setLessonId(exam.getLesson().getId());
            dto.setLessonTitle(exam.getLesson().getTitle());
        }

        // Helper fields
        dto.setCanBeModified(exam.canBeModified());
        dto.setAvailableForStudents(exam.isAvailableForStudents());

        // Questions count (avoid loading all questions for performance)
        if (exam.getQuestions() != null) {
            dto.setQuestionCount(exam.getQuestions().size());

            // Only include full questions if they're already loaded and not too many
            if (exam.getQuestions().size() <= 20) { // Reasonable limit
                List<QuestionDTO> questionDTOs = exam.getQuestions().stream()
                        .map(this::mapToQuestionDTO)
                        .collect(Collectors.toList());
                dto.setQuestions(questionDTOs);
            }
        } else {
            dto.setQuestionCount(0);
        }

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
        dto.setCompletedContent(progress.getCompletedContent());
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
            dto.setTimeLimit(submission.getExam().getTimeLimit());

            if (submission.getExam().getLesson() != null &&
                    submission.getExam().getLesson().getCourse() != null) {
                dto.setCourseTitle(submission.getExam().getLesson().getCourse().getTitle());
            }
        }

        dto.setSubmissionTime(submission.getSubmissionTime());
        dto.setScore(submission.getScore());
        dto.setPassed(submission.isPassed());
        dto.setAnswers(submission.getAnswers());
        dto.setActualDuration(submission.getTimeSpent());

        return dto;
    }

    public ChatMessageDTO mapToChatMessageDTO(ChatMessage message) {
        if (message == null) {
            return null;
        }

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(message.getId());

        if (message.getCourse() != null) {
            dto.setCourseId(message.getCourse().getId());
            dto.setCourseName(message.getCourse().getTitle());
        }

        if (message.getSender() != null) {
            dto.setSenderId(message.getSender().getId());
            dto.setSenderName(message.getSender().getFirstName() + " " +
                    message.getSender().getLastName());
        }

        dto.setContent(message.getContent());
        dto.setSentAt(message.getSentAt());
        dto.setReadBy(message.getReadBy());

        return dto;
    }

    /**
     * Maps an Assignment entity to an AssignmentDTO
     */
    public AssignmentDTO mapToAssignmentDTO(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        AssignmentDTO dto = new AssignmentDTO();
        dto.setId(assignment.getId());
        dto.setTitle(assignment.getTitle());
        dto.setDescription(assignment.getDescription());

        if (assignment.getLesson() != null) {
            dto.setLessonId(assignment.getLesson().getId());
        }

        if (assignment.getTeacher() != null) {
            dto.setTeacherId(assignment.getTeacher().getId());
            dto.setTeacherName(assignment.getTeacher().getFirstName() + " " +
                    assignment.getTeacher().getLastName());
        }

        if (assignment.getFile() != null) {
            dto.setFile(mapToFileMetadataDTO(assignment.getFile()));
        }

        dto.setCreatedAt(assignment.getCreatedAt());
        dto.setDueDate(assignment.getDueDate());

        return dto;
    }

    /**
     * Maps an AssignmentSubmission entity to an AssignmentSubmissionDTO
     */
    public AssignmentSubmissionDTO mapToAssignmentSubmissionDTO(AssignmentSubmission submission) {
        if (submission == null) {
            return null;
        }

        AssignmentSubmissionDTO dto = new AssignmentSubmissionDTO();
        dto.setId(submission.getId());

        if (submission.getAssignment() != null) {
            dto.setAssignmentId(submission.getAssignment().getId());
            dto.setAssignmentTitle(submission.getAssignment().getTitle());
        }

        if (submission.getStudent() != null) {
            dto.setStudentId(submission.getStudent().getId());
            dto.setStudentName(submission.getStudent().getFirstName() + " " +
                    submission.getStudent().getLastName());
        }

        dto.setComment(submission.getComment());

        if (submission.getFile() != null) {
            dto.setFile(mapToFileMetadataDTO(submission.getFile()));
        }

        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setScore(submission.getScore());
        dto.setFeedback(submission.getFeedback());
        dto.setGraded(submission.isGraded());
        dto.setGradedAt(submission.getGradedAt());

        return dto;
    }

    /**
     * Maps a list of Assignments to DTOs
     */
    public List<AssignmentDTO> mapToAssignmentDTOList(List<Assignment> assignments) {
        return assignments.stream()
                .map(this::mapToAssignmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Maps a list of Assignment submissions to DTOs
     */
    public List<AssignmentSubmissionDTO> mapToAssignmentSubmissionDTOList(List<AssignmentSubmission> submissions) {
        return submissions.stream()
                .map(this::mapToAssignmentSubmissionDTO)
                .collect(Collectors.toList());
    }

    public List<ChatMessageDTO> mapToChatMessageDTOList(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::mapToChatMessageDTO)
                .collect(Collectors.toList());
    }

    public ExamDTO mapToExamDTOWithoutQuestions(Exam exam) {
        if (exam == null) {
            return null;
        }

        ExamDTO dto = new ExamDTO();
        dto.setId(exam.getId());
        dto.setTitle(exam.getTitle());
        dto.setDescription(exam.getDescription());
        dto.setTimeLimit(exam.getTimeLimit());
        dto.setPassingScore(exam.getPassingScore());

        if (exam.getStatus() != null) {
            dto.setStatus(exam.getStatus().toString());
        }
        dto.setFinalizedAt(exam.getFinalizedAt());

        if (exam.getFinalizedBy() != null) {
            String finalizerName = exam.getFinalizedBy().getFirstName();
            if (exam.getFinalizedBy().getLastName() != null) {
                finalizerName += " " + exam.getFinalizedBy().getLastName();
            }
            dto.setFinalizedBy(finalizerName);
        }

        dto.setTotalPossibleScore(exam.getTotalPossibleScore());
        dto.setAvailableFrom(exam.getAvailableFrom());
        dto.setAvailableTo(exam.getAvailableTo());

        if (exam.getLesson() != null) {
            dto.setLessonId(exam.getLesson().getId());
            dto.setLessonTitle(exam.getLesson().getTitle());
        }

        dto.setCanBeModified(exam.canBeModified());
        dto.setAvailableForStudents(exam.isAvailableForStudents());

        // Only count, no questions data
        dto.setQuestionCount(exam.getQuestions() != null ? exam.getQuestions().size() : 0);

        return dto;
    }

    public QuestionDTO mapToQuestionDTO(Question question) {
        if (question == null) {
            return null;
        }

        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setQuestionType(question.getQuestionType());
        dto.setPoints(question.getPoints());
        dto.setExplanation(question.getExplanation());
        dto.setHint(question.getHint());
        dto.setTemplate(question.getTemplate());
        dto.setTimeLimit(question.getTimeLimit());
        dto.setIsRequired(question.getIsRequired());
        dto.setDifficulty(question.getDifficulty());

        // Map answers based on question type
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
            case CATEGORIZATION:
                if (question.getAnswers() != null) {
                    List<AnswerDTO> answerDTOs = question.getAnswers().stream()
                            .map(this::mapToAnswerDTO)
                            .collect(Collectors.toList());
                    dto.setAnswers(answerDTOs);
                }
                break;

            case FILL_IN_THE_BLANKS:
                if (question.getBlankAnswers() != null) {
                    List<BlankAnswerDTO> blankDTOs = question.getBlankAnswers().stream()
                            .map(this::mapToBlankAnswerDTO)
                            .collect(Collectors.toList());
                    dto.setBlankAnswers(blankDTOs);
                }
                break;

            case MATCHING:
                if (question.getMatchingPairs() != null) {
                    List<MatchingPairDTO> matchingDTOs = question.getMatchingPairs().stream()
                            .map(this::mapToMatchingPairDTO)
                            .collect(Collectors.toList());
                    dto.setMatchingPairs(matchingDTOs);
                }
                break;
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
        dto.setCorrect(answer.getCorrect());
        dto.setAnswerType(answer.getAnswerType());
        dto.setMediaUrl(answer.getMediaUrl());
        dto.setPoints(answer.getPoints());
        dto.setFeedback(answer.getFeedback());
        dto.setOrderIndex(answer.getOrderIndex());
        dto.setCategory(answer.getCategory());

        return dto;
    }

    public BlankAnswerDTO mapToBlankAnswerDTO(BlankAnswer blankAnswer) {
        if (blankAnswer == null) {
            return null;
        }

        BlankAnswerDTO dto = new BlankAnswerDTO();
        dto.setBlankIndex(blankAnswer.getBlankIndex());
        dto.setCorrectAnswer(blankAnswer.getCorrectAnswer());
        dto.setCaseSensitive(blankAnswer.getCaseSensitive());
        dto.setPoints(blankAnswer.getPoints());

        // Parse acceptable answers from string to list
        if (blankAnswer.getAcceptableAnswers() != null) {
            dto.setAcceptableAnswers(Arrays.asList(blankAnswer.getAcceptableAnswers().split(",")));
        }

        return dto;
    }

    public MatchingPairDTO mapToMatchingPairDTO(MatchingPair pair) {
        if (pair == null) {
            return null;
        }

        MatchingPairDTO dto = new MatchingPairDTO();
        dto.setLeftItem(pair.getLeftItem());
        dto.setRightItem(pair.getRightItem());
        dto.setLeftItemType(pair.getLeftItemType());
        dto.setRightItemType(pair.getRightItemType());
        dto.setLeftItemUrl(pair.getLeftItemUrl());
        dto.setRightItemUrl(pair.getRightItemUrl());
        dto.setPoints(pair.getPoints());

        return dto;
    }

    public ExamWithDetailsDTO mapToExamWithDetailsDTO(Exam exam, List<Submission> submissions) {
        if (exam == null) {
            return null;
        }

        ExamWithDetailsDTO dto = new ExamWithDetailsDTO();
        dto.setId(exam.getId());
        dto.setTitle(exam.getTitle());
        dto.setDescription(exam.getDescription());
        dto.setDuration(exam.getTimeLimit());
        dto.setPassingScore(exam.getPassingScore());

        // Set status
        if (exam.getStatus() != null) {
            dto.setStatus(exam.getStatus().toString());
        }

        // Use finalizedAt as createdAt if available, otherwise null
        dto.setCreatedAt(exam.getFinalizedAt());

        // Set question count
        dto.setQuestionCount(exam.getQuestions() != null ? exam.getQuestions().size() : 0);

        // Map lesson information
        if (exam.getLesson() != null) {
            dto.setLessonId(exam.getLesson().getId());

            ExamWithDetailsDTO.LessonSummary lessonSummary = new ExamWithDetailsDTO.LessonSummary();
            lessonSummary.setId(exam.getLesson().getId());
            lessonSummary.setTitle(exam.getLesson().getTitle());

            // Map course information
            if (exam.getLesson().getCourse() != null) {
                ExamWithDetailsDTO.CourseSummary courseSummary = new ExamWithDetailsDTO.CourseSummary();
                courseSummary.setId(exam.getLesson().getCourse().getId());
                courseSummary.setTitle(exam.getLesson().getCourse().getTitle());
                lessonSummary.setCourse(courseSummary);
            }

            dto.setLesson(lessonSummary);
        }

        // Map submissions
        if (submissions != null) {
            List<ExamWithDetailsDTO.SubmissionSummary> submissionSummaries = submissions.stream()
                    .map(submission -> {
                        ExamWithDetailsDTO.SubmissionSummary summary = new ExamWithDetailsDTO.SubmissionSummary();
                        summary.setId(submission.getId());
                        summary.setStudentId(submission.getStudent().getId());
                        summary.setScore(submission.getScore());
                        summary.setSubmittedAt(submission.getSubmissionTime());
                        return summary;
                    })
                    .collect(Collectors.toList());
            dto.setSubmissions(submissionSummaries);
        }

        return dto;
    }

    public List<ExamWithDetailsDTO> mapToExamWithDetailsDTOList(List<Exam> exams, Map<Long, List<Submission>> submissionsByExam) {
        return exams.stream()
                .map(exam -> {
                    List<Submission> examSubmissions = submissionsByExam.getOrDefault(exam.getId(), new ArrayList<>());
                    return mapToExamWithDetailsDTO(exam, examSubmissions);
                })
                .collect(Collectors.toList());
    }

    /**
     * Maps Content entity to ContentDetailsDTO with enhanced information
     */
    public ContentDetailsDTO mapToContentDetailsDTO(Content content) {
        if (content == null) {
            return null;
        }

        ContentDetailsDTO dto = new ContentDetailsDTO();
        dto.setId(content.getId());
        dto.setTitle(content.getTitle());
        dto.setType(content.getType());
        dto.setTextContent(content.getTextContent());
        dto.setOrderIndex(content.getOrderIndex());

        // Handle file information
        if (content.getFile() != null) {
            dto.setFileId(content.getFile().getId());
            dto.setFileUrl("/api/content/files/" + content.getFile().getId());
        }

        // Note: Content entity doesn't have createdAt/updatedAt fields
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);

        // Map lesson information
        if (content.getLesson() != null) {
            ContentDetailsDTO.LessonInfo lessonInfo = new ContentDetailsDTO.LessonInfo();
            lessonInfo.setId(content.getLesson().getId());
            lessonInfo.setTitle(content.getLesson().getTitle());

            // Map course information
            if (content.getLesson().getCourse() != null) {
                lessonInfo.setCourseId(content.getLesson().getCourse().getId());
                lessonInfo.setCourseTitle(content.getLesson().getCourse().getTitle());
            }

            dto.setLesson(lessonInfo);
        }

        return dto;
    }

    public UserDTO mapToUserDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setNationalId(user.getNationalId());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAge(user.getAge());
        dto.setEmail(user.getEmail());

        // Map roles
        if (user.getRoles() != null) {
            List<UserDTO.RoleDTO> roleDTOs = user.getRoles().stream()
                    .map(role -> {
                        UserDTO.RoleDTO roleDTO = new UserDTO.RoleDTO();
                        roleDTO.setId(role.getId());
                        roleDTO.setName(role.getName());
                        return roleDTO;
                    })
                    .collect(Collectors.toList());
            dto.setRoles(roleDTOs);
        }

        return dto;
    }

    public List<UserDTO> mapToUserDTOList(List<User> users) {
        if (users == null) {
            return new ArrayList<>();
        }

        return users.stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }
}