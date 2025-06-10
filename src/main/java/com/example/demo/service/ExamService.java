package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.ExamRepository;
import com.example.demo.repository.LessonRepository;
import com.example.demo.repository.QuestionRepository;
import com.example.demo.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;

    public ExamService(
            ExamRepository examRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository) {
        this.examRepository = examRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public Exam createExam(Exam exam, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // Set default status to DRAFT
        exam.setStatus(ExamStatus.DRAFT);
        
        // Save exam first
        Exam savedExam = examRepository.save(exam);

        // Update lesson with exam
        lesson.setExam(savedExam);
        lessonRepository.save(lesson);

        return savedExam;
    }

    public Exam getExamById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
    }

    public Exam getExamByLessonId(Long lessonId) {
        return examRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new RuntimeException("Exam not found for this lesson"));
    }

    public Question addQuestion(Question question, Long examId) {
        Exam exam = getExamById(examId);
        
        // Check if exam can be modified
        if (!exam.canBeModified()) {
            throw new RuntimeException("Cannot add questions to finalized exam");
        }
        
        question.setExam(exam);
        return questionRepository.save(question);
    }

    public List<Question> getExamQuestions(Long examId) {
        Exam exam = getExamById(examId);
        return questionRepository.findByExamOrderById(exam);
    }

    @Transactional
    public Submission submitExam(Long examId, User student, Map<Long, Long> answers) {
        Exam exam = getExamById(examId);
        
        // Check if exam is available for students
        if (!exam.isAvailableForStudents()) {
            throw new RuntimeException("Exam is not available for submission");
        }

        Submission submission = new Submission();
        submission.setStudent(student);
        submission.setExam(exam);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setAnswers(answers);

        // Calculate score
        int totalPoints = 0;
        int earnedPoints = 0;

        List<Question> questions = questionRepository.findByExamOrderById(exam);
        for (Question question : questions) {
            totalPoints += question.getPoints();

            Long answerId = answers.get(question.getId());
            if (answerId != null) {
                // Check if correct answer
                boolean isCorrect = question.getAnswers().stream()
                        .filter(answer -> answer.getId().equals(answerId))
                        .findFirst()
                        .map(Answer::getCorrect)
                        .orElse(false);

                if (isCorrect) {
                    earnedPoints += question.getPoints();
                }
            }
        }

        submission.setScore(earnedPoints);
        submission.setPassed(earnedPoints >= exam.getPassingScore());

        return submissionRepository.save(submission);
    }

    public List<Submission> getStudentSubmissions(User student) {
        return submissionRepository.findByStudent(student);
    }

    public List<Submission> getExamSubmissions(Long examId) {
        Exam exam = getExamById(examId);
        return submissionRepository.findByExam(exam);
    }

    @Transactional
    public Question cloneQuestionFromBank(Long examId, Long questionId) {
        Exam exam = getExamById(examId);
        
        // Check if exam can be modified
        if (!exam.canBeModified()) {
            throw new RuntimeException("Cannot add questions to finalized exam");
        }
        
        Question bankQuestion = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Bank question not found"));

        if (!bankQuestion.getInBank()) {
            throw new RuntimeException("Not a bank question");
        }

        // Create a new question based on bank question
        Question newQuestion = new Question();
        newQuestion.setText(bankQuestion.getText());
        newQuestion.setPoints(bankQuestion.getPoints());
        newQuestion.setExam(exam);
        newQuestion.setInBank(false);

        // Clone answers
        for (Answer answer : bankQuestion.getAnswers()) {
            Answer newAnswer = new Answer();
            newAnswer.setText(answer.getText());
            newAnswer.setCorrect(answer.getCorrect());
            newQuestion.getAnswers().add(newAnswer);
        }

        return questionRepository.save(newQuestion);
    }

    /**
     * Finalize an exam - make it ready for students
     */
    @Transactional
    public Exam finalizeExam(Long examId, User teacher) {
        Exam exam = getExamById(examId);
        
        // Security check: verify teacher owns the exam
        if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only finalize your own exams");
        }
        
        // Validation checks
        if (exam.getStatus() == ExamStatus.FINALIZED) {
            throw new RuntimeException("Exam is already finalized");
        }
        
        List<Question> questions = questionRepository.findByExamOrderById(exam);
        
        // Ensure exam has at least one question
        if (questions.isEmpty()) {
            throw new RuntimeException("Cannot finalize exam: Exam must have at least one question");
        }
        
        // Validate all questions have valid answers
        for (Question question : questions) {
            if (question.getAnswers().isEmpty()) {
                throw new RuntimeException("Cannot finalize exam: Question '" + question.getText() + "' has no answers");
            }
            
            // Check if at least one answer is marked as correct
            boolean hasCorrectAnswer = question.getAnswers().stream()
                    .anyMatch(Answer::getCorrect);
            
            if (!hasCorrectAnswer) {
                throw new RuntimeException("Cannot finalize exam: Question '" + question.getText() + "' has no correct answer");
            }
        }
        
        // Calculate total possible score
        int totalScore = questions.stream()
                .mapToInt(Question::getPoints)
                .sum();
        
        // Validate passing score
        if (exam.getPassingScore() > totalScore) {
            throw new RuntimeException("Cannot finalize exam: Passing score (" + exam.getPassingScore() + 
                    ") cannot be greater than total possible score (" + totalScore + ")");
        }
        
        // Update exam status and metadata
        exam.setStatus(ExamStatus.FINALIZED);
        exam.setFinalizedAt(LocalDateTime.now());
        exam.setFinalizedBy(teacher);
        exam.setTotalPossibleScore(totalScore);
        
        // Set availability start time to now (can be customized later)
        exam.setAvailableFrom(LocalDateTime.now());
        
        return examRepository.save(exam);
    }

    /**
     * Get exam details for finalization validation
     */
    public Map<String, Object> getExamFinalizationInfo(Long examId, User teacher) {
        Exam exam = getExamById(examId);
        
        // Security check
        if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only view your own exams");
        }
        
        List<Question> questions = questionRepository.findByExamOrderById(exam);
        
        // Calculate validation results
        boolean hasQuestions = !questions.isEmpty();
        boolean allQuestionsValid = true;
        int totalScore = 0;
        
        for (Question question : questions) {
            totalScore += question.getPoints();
            
            if (question.getAnswers().isEmpty()) {
                allQuestionsValid = false;
                break;
            }
            
            boolean hasCorrectAnswer = question.getAnswers().stream()
                    .anyMatch(Answer::getCorrect);
            
            if (!hasCorrectAnswer) {
                allQuestionsValid = false;
                break;
            }
        }
        
        boolean canFinalize = hasQuestions && allQuestionsValid && 
                            exam.getStatus() == ExamStatus.DRAFT &&
                            exam.getPassingScore() <= totalScore;
        
        // Safely get finalizer name
        String finalizerName = null;
        if (exam.getFinalizedBy() != null) {
            finalizerName = exam.getFinalizedBy().getFirstName();
            if (exam.getFinalizedBy().getLastName() != null) {
                finalizerName += " " + exam.getFinalizedBy().getLastName();
            }
        }
        
        // Use HashMap instead of Map.of to avoid the 10 parameter limit
        Map<String, Object> result = new HashMap<>();
        result.put("examId", exam.getId());
        result.put("title", exam.getTitle());
        result.put("status", exam.getStatus().toString());
        result.put("questionCount", questions.size());
        result.put("totalPossibleScore", totalScore);
        result.put("passingScore", exam.getPassingScore());
        result.put("hasQuestions", hasQuestions);
        result.put("allQuestionsValid", allQuestionsValid);
        result.put("canFinalize", canFinalize);
        result.put("finalizedAt", exam.getFinalizedAt());
        result.put("finalizedBy", finalizerName);
        
        return result;
    }
    public List<Exam> getExamsByTeacher(User teacher) {
        return examRepository.findByTeacher(teacher);
    }

    /**
     * Get exams with submissions for a teacher
     */
    public Map<Long, List<Submission>> getSubmissionsByExamsForTeacher(User teacher) {
        List<Exam> teacherExams = getExamsByTeacher(teacher);
        Map<Long, List<Submission>> submissionsByExam = new HashMap<>();

        for (Exam exam : teacherExams) {
            List<Submission> submissions = submissionRepository.findByExam(exam);
            submissionsByExam.put(exam.getId(), submissions);
        }

        return submissionsByExam;
    }
    /**
     * Get available exams for a student
     */
    public List<Exam> getAvailableExamsForStudent(User student) {
        // Get all courses the student is enrolled in
        List<Course> enrolledCourses = courseRepository.findByEnrolledStudentsContaining(student);

        List<Exam> availableExams = new ArrayList<>();

        for (Course course : enrolledCourses) {
            List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);

            for (Lesson lesson : lessons) {
                if (lesson.getExam() != null) {
                    Exam exam = lesson.getExam();

                    // Check if exam is available for students
                    if (exam.isAvailableForStudents()) {
                        availableExams.add(exam);
                    }
                }
            }
        }

        return availableExams;
    }
}