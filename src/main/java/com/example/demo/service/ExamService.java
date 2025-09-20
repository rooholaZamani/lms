package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService;
    public ExamService(
            ExamRepository examRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository, CourseRepository courseRepository, UserService userService) {
        this.examRepository = examRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.userService = userService;
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
    public Submission submitExam(Long examId, User student, String answersJson) {
        Exam exam = getExamById(examId);

        // Check if exam is available for students
        if (!exam.isAvailableForStudents()) {
            throw new RuntimeException("Exam is not available for submission");
        }

        Optional<Submission> existingSubmission = submissionRepository.findByStudentAndExam(student, exam);
        if (existingSubmission.isPresent()) {
            throw new RuntimeException("You have already taken this exam. Multiple attempts are not allowed.");
        }

        Submission submission = new Submission();
        submission.setStudent(student);
        submission.setExam(exam);
        submission.setSubmissionTime(LocalDateTime.now());
        submission.setAnswersJson(answersJson);

        // Calculate score based on question types
        int[] scoreResult = calculateScore(exam, answersJson);
        int earnedPoints = scoreResult[0];
        int totalPoints = scoreResult[1];

        // Validate score calculation
        if (earnedPoints < 0) {
            System.err.println("ERROR: Negative earned points detected: " + earnedPoints);
            earnedPoints = 0;
        }

        if (earnedPoints > totalPoints) {
            System.err.println("ERROR: Earned points (" + earnedPoints + ") exceed total possible points (" + totalPoints + ")");
            earnedPoints = totalPoints;
        }

        if (totalPoints <= 0) {
            System.err.println("ERROR: Invalid total points: " + totalPoints);
            // Don't save submission if exam has no valid questions
            throw new RuntimeException("Invalid exam: Total possible score is " + totalPoints);
        }

        // Validate against exam's total possible score
        if (exam.getTotalPossibleScore() != null && exam.getTotalPossibleScore() != totalPoints) {
            System.err.println("WARNING: Calculated total points (" + totalPoints + ") doesn't match exam's stored total (" + exam.getTotalPossibleScore() + ")");
            // Update exam's total score if it's wrong
            exam.setTotalPossibleScore(totalPoints);
            examRepository.save(exam);
        }

        // Validate passing score
        if (exam.getPassingScore() > totalPoints) {
            System.err.println("ERROR: Passing score (" + exam.getPassingScore() + ") exceeds total possible points (" + totalPoints + ")");
        }

        submission.setScore(earnedPoints);
        submission.setPassed(earnedPoints >= exam.getPassingScore());

        System.out.println("Final submission score: " + earnedPoints + "/" + totalPoints + " (Passed: " + submission.isPassed() + ")");

        return submissionRepository.save(submission);
    }


    private Map<String, Object> parseAnswersJson(String answersJson) {
        if (answersJson == null || answersJson.trim().isEmpty()) {
            System.out.println("WARNING: Empty or null answers JSON provided");
            return new HashMap<>();
        }

        try {
            Map<String, Object> parsedAnswers = objectMapper.readValue(answersJson, new TypeReference<Map<String, Object>>() {});
            System.out.println("Successfully parsed answers JSON. Keys: " + parsedAnswers.keySet());
            return parsedAnswers;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to parse answers JSON: " + answersJson);
            System.err.println("Error details: " + e.getMessage());
            e.printStackTrace();

            // Try to parse as simple key-value pairs manually as fallback
            try {
                Map<String, Object> fallbackAnswers = parseAnswersJsonManually(answersJson);
                System.out.println("Fallback parsing successful. Keys: " + fallbackAnswers.keySet());
                return fallbackAnswers;
            } catch (Exception fallbackException) {
                System.err.println("ERROR: Fallback parsing also failed: " + fallbackException.getMessage());
                return new HashMap<>();
            }
        }
    }

    private Map<String, Object> parseAnswersJsonManually(String answersJson) {
        Map<String, Object> answers = new HashMap<>();

        // Remove outer braces and split by comma
        String content = answersJson.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            content = content.substring(1, content.length() - 1);
        }

        if (content.trim().isEmpty()) {
            return answers;
        }

        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replaceAll("[\"\']", "");
                String value = keyValue[1].trim().replaceAll("[\"\']", "");

                // Try to parse value as number if possible
                try {
                    if (value.contains(".")) {
                        answers.put(key, Double.parseDouble(value));
                    } else {
                        answers.put(key, Long.parseLong(value));
                    }
                } catch (NumberFormatException e) {
                    answers.put(key, value);
                }
            }
        }

        return answers;
    }



    public List<Submission> getStudentSubmissions(User student) {
        return submissionRepository.findByStudent(student);
    }

    public List<Submission> getExamSubmissions(Long examId) {
        Exam exam = getExamById(examId);
        return submissionRepository.findByExam(exam);
    }
    /**
     * Check if student has already taken the exam
     */
    public boolean hasStudentTakenExam(Long examId, User student) {
        Exam exam = getExamById(examId);
        return submissionRepository.findByStudentAndExam(student, exam).isPresent();
    }

    /**
     * Get student's submission for an exam (if exists)
     */
    public Optional<Submission> getStudentSubmission(Long examId, User student) {
        Exam exam = getExamById(examId);
        return submissionRepository.findByStudentAndExam(student, exam);
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

        // Validate all questions have valid answers based on question type
        for (Question question : questions) {
            boolean hasValidAnswers = false;

            switch (question.getQuestionType()) {
                case MULTIPLE_CHOICE:
                case TRUE_FALSE:
                    // These use the answers field with correct flag
                    if (!question.getAnswers().isEmpty()) {
                        // Check if at least one answer is marked as correct
                        hasValidAnswers = question.getAnswers().stream()
                                .anyMatch(Answer::getCorrect);
                    }
                    break;

                case CATEGORIZATION:
                    // These use the answers field with category information
                    if (!question.getAnswers().isEmpty()) {
                        // Check if all answers have valid categories
                        hasValidAnswers = question.getAnswers().stream()
                                .allMatch(answer -> answer.getCategory() != null && !answer.getCategory().trim().isEmpty());
                    }
                    break;

                case FILL_IN_THE_BLANKS:
                    // These use blankAnswers field
                    hasValidAnswers = !question.getBlankAnswers().isEmpty();
                    break;

                case MATCHING:
                    // These use matchingPairs field
                    hasValidAnswers = !question.getMatchingPairs().isEmpty();
                    break;

                case ESSAY:
                case SHORT_ANSWER:
                    // These don't require predefined answers
                    hasValidAnswers = true;
                    break;
            }

            if (!hasValidAnswers) {
                throw new RuntimeException("Cannot finalize exam: Question '" + question.getText() + "' has invalid or missing answers for question type " + question.getQuestionType());
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

            boolean hasValidAnswers = false;

            switch (question.getQuestionType()) {
                case MULTIPLE_CHOICE:
                case TRUE_FALSE:
                    // These use the answers field with correct flag
                    if (!question.getAnswers().isEmpty()) {
                        hasValidAnswers = question.getAnswers().stream()
                                .anyMatch(Answer::getCorrect);
                    }
                    break;

                case CATEGORIZATION:
                    // These use the answers field with category information
                    if (!question.getAnswers().isEmpty()) {
                        hasValidAnswers = question.getAnswers().stream()
                                .allMatch(answer -> answer.getCategory() != null && !answer.getCategory().trim().isEmpty());
                    }
                    break;

                case FILL_IN_THE_BLANKS:
                    // These use blankAnswers field
                    hasValidAnswers = !question.getBlankAnswers().isEmpty();
                    break;

                case MATCHING:
                    // These use matchingPairs field
                    hasValidAnswers = !question.getMatchingPairs().isEmpty();
                    break;

                case ESSAY:
                case SHORT_ANSWER:
                    // These don't require predefined answers
                    hasValidAnswers = true;
                    break;
            }

            if (!hasValidAnswers) {
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

    @Transactional
    public void deleteExam(Long examId, User teacher) {
        Exam exam = getExamById(examId);

        // Security check: verify teacher owns the exam
        if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only delete your own exams");
        }

        // Business rule: only allow deletion of DRAFT exams
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new RuntimeException("Cannot delete finalized exam. Only draft exams can be deleted.");
        }

        // Check if any students have submitted this exam
        List<Submission> submissions = submissionRepository.findByExam(exam);
        if (!submissions.isEmpty()) {
            throw new RuntimeException("Cannot delete exam: " + submissions.size() + " student(s) have already submitted this exam");
        }

        // Remove exam reference from lesson
        Lesson lesson = exam.getLesson();
        lesson.setExam(null);
        lessonRepository.save(lesson);

        // Delete the exam (questions will be cascade deleted due to CascadeType.ALL)
        examRepository.delete(exam);
    }


    @Transactional
    public Question updateQuestion(Long questionId, Question questionDetails, User teacher) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Exam exam = question.getExam();

        // Security check: verify teacher owns the exam
        if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only update questions in your own exams");
        }

        // Business rule: only allow updating if exam is still in draft status
        if (!exam.canBeModified()) {
            throw new RuntimeException("Cannot update questions in finalized exam");
        }

        // Update question fields
        question.setText(questionDetails.getText());
        question.setQuestionType(questionDetails.getQuestionType());
        question.setPoints(questionDetails.getPoints());
        question.setExplanation(questionDetails.getExplanation());
        question.setHint(questionDetails.getHint());
        question.setTimeLimit(questionDetails.getTimeLimit());
        question.setDifficulty(questionDetails.getDifficulty());
        question.setIsRequired(questionDetails.getIsRequired());

        // Clear existing related data and add new ones based on question type
        question.getAnswers().clear();
        question.getBlankAnswers().clear();
        question.getMatchingPairs().clear();
        question.getCategories().clear();

        // Copy new related data from questionDetails
        if (questionDetails.getAnswers() != null) {
            for (Answer answer : questionDetails.getAnswers()) {
                question.getAnswers().add(answer);
            }
        }

        if (questionDetails.getBlankAnswers() != null) {
            for (BlankAnswer blankAnswer : questionDetails.getBlankAnswers()) {
                question.getBlankAnswers().add(blankAnswer);
            }
        }

        if (questionDetails.getMatchingPairs() != null) {
            for (MatchingPair pair : questionDetails.getMatchingPairs()) {
                question.getMatchingPairs().add(pair);
            }
        }

        if (questionDetails.getCategories() != null) {
            question.getCategories().addAll(questionDetails.getCategories());
        }

        return questionRepository.save(question);
    }
    @Transactional
    public void deleteQuestion(Long questionId, User teacher) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        Exam exam = question.getExam();

        // Security check: verify teacher owns the exam
        if (!exam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Unauthorized: You can only delete questions from your own exams");
        }

        // Business rule: only allow deletion if exam is still in draft status
        if (!exam.canBeModified()) {
            throw new RuntimeException("Cannot delete questions from finalized exam");
        }

        // Check if any students have submitted this exam
        List<Submission> submissions = submissionRepository.findByExam(exam);
        if (!submissions.isEmpty()) {
            throw new RuntimeException("Cannot delete question: " + submissions.size() +
                    " student(s) have already submitted this exam");
        }

        // Ensure exam has at least 2 questions (can't delete the last question)
        List<Question> examQuestions = questionRepository.findByExamOrderById(exam);
        if (examQuestions.size() <= 1) {
            throw new RuntimeException("Cannot delete question: Exam must have at least one question");
        }

        // Delete the question (answers, blank answers, matching pairs will be cascade deleted)
        questionRepository.delete(question);
    }
    @Transactional
    public Submission updateSubmissionTimeSpent(Submission submission, Long timeSpent) {
        submission.setTimeSpent(timeSpent);
        return submissionRepository.save(submission);
    }

    // Helper method to convert old Map<Long, Long> format to JSON for backward compatibility
    public String convertOldAnswersToJson(Map<Long, Long> oldAnswers) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<Long, Long> entry : oldAnswers.entrySet()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(entry.getKey()).append("\":")
                    .append("\"").append(entry.getValue()).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    // Method to validate question type support
    private boolean isQuestionTypeSupported(QuestionType type) {
        return type == QuestionType.MULTIPLE_CHOICE ||
                type == QuestionType.TRUE_FALSE ||
                type == QuestionType.CATEGORIZATION ||
                type == QuestionType.MATCHING ||
                type == QuestionType.FILL_IN_THE_BLANKS;
    }
    public Exam findById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found with id: " + examId));
    }





    public boolean evaluateAnswer(Question question, Object studentAnswer) {
        if (studentAnswer == null) {
            System.out.println("Student answer is null for question " + question.getId());
            return false;
        }

        System.out.println("Evaluating " + question.getQuestionType() + " question");
        System.out.println("Student answer type: " + studentAnswer.getClass().getSimpleName());
        System.out.println("Student answer value: " + studentAnswer);

        boolean result;
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                result = evaluateSimpleAnswer(question, studentAnswer);
                break;
            case CATEGORIZATION:
                result = evaluateCategorizationAnswer(question, studentAnswer);
                break;
            case MATCHING:
                result = evaluateMatchingAnswer(question, studentAnswer);
                break;
            case FILL_IN_THE_BLANKS:
                result = evaluateFillBlankAnswer(question, studentAnswer);
                break;
            case SHORT_ANSWER:
                result = evaluateShortAnswer(question, studentAnswer);
                break;
            default:
                System.out.println("WARNING: Unsupported question type: " + question.getQuestionType());
                result = false;
        }

        System.out.println("Evaluation result: " + result);
        return result;
    }

    public Object evaluateAnswerWithPartialScoring(Question question, Object studentAnswer) {
        if (studentAnswer == null) {
            System.out.println("Student answer is null for question " + question.getId());
            return false;
        }

        System.out.println("Evaluating " + question.getQuestionType() + " question with partial scoring support");
        System.out.println("Student answer type: " + studentAnswer.getClass().getSimpleName());
        System.out.println("Student answer value: " + studentAnswer);

        Object result;
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                result = evaluateSimpleAnswer(question, studentAnswer);
                break;
            case CATEGORIZATION:
                result = evaluateCategorizationAnswerPartial(question, studentAnswer);
                break;
            case MATCHING:
                result = evaluateMatchingAnswerPartial(question, studentAnswer);
                break;
            case FILL_IN_THE_BLANKS:
                result = evaluateFillBlankAnswer(question, studentAnswer);
                break;
            case SHORT_ANSWER:
                result = evaluateShortAnswer(question, studentAnswer);
                break;
            case ESSAY:
                // Essay questions require manual grading, so we return true to give full points initially
                // The actual score will be adjusted during manual grading
                result = true;
                System.out.println("ESSAY question marked for manual grading - giving full points initially");
                break;
            default:
                System.out.println("WARNING: Unsupported question type: " + question.getQuestionType());
                result = false;
        }

        System.out.println("Evaluation result: " + result);
        return result;
    }
    private boolean evaluateSimpleAnswer(Question question, Object studentAnswer) {
        try {
            System.out.println("Evaluating simple answer...");
            System.out.println("Question type: " + question.getQuestionType());
            System.out.println("Available answers:");
            for (Answer answer : question.getAnswers()) {
                System.out.println("  Answer ID: " + answer.getId() + ", Text: '" + answer.getText() + "', Correct: " + answer.getCorrect());
            }

            // Handle TRUE_FALSE questions differently - they send string values instead of IDs
            if (question.getQuestionType() == QuestionType.TRUE_FALSE) {
                return evaluateTrueFalseAnswer(question, studentAnswer);
            }

            // For MULTIPLE_CHOICE questions, use the existing ID-based logic
            Long answerId;
            if (studentAnswer instanceof Number) {
                answerId = ((Number) studentAnswer).longValue();
                System.out.println("Parsed student answer as Number: " + answerId);
            } else if (studentAnswer instanceof String) {
                answerId = Long.parseLong((String) studentAnswer);
                System.out.println("Parsed student answer as String to Long: " + answerId);
            } else {
                System.out.println("ERROR: Cannot parse student answer - unsupported type: " + studentAnswer.getClass().getSimpleName());
                return false;
            }

            Answer selectedAnswer = question.getAnswers().stream()
                    .filter(answer -> answer.getId().equals(answerId))
                    .findFirst()
                    .orElse(null);

            if (selectedAnswer != null) {
                System.out.println("Found matching answer: '" + selectedAnswer.getText() + "', Correct: " + selectedAnswer.getCorrect());
                return selectedAnswer.getCorrect();
            } else {
                System.out.println("ERROR: No answer found with ID: " + answerId);
                return false;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Failed to parse answer ID from: " + studentAnswer + " - " + e.getMessage());
            return false;
        }
    }

    private boolean evaluateTrueFalseAnswer(Question question, Object studentAnswer) {
        try {
            System.out.println("Evaluating TRUE_FALSE answer...");

            String studentValue = studentAnswer.toString().toLowerCase().trim();
            System.out.println("Student answer value (normalized): '" + studentValue + "'");

            // Check each answer option to find the one that matches the student's input
            for (Answer answer : question.getAnswers()) {
                String answerText = answer.getText().toLowerCase().trim();
                System.out.println("Checking answer: '" + answerText + "', Correct: " + answer.getCorrect());

                // Match the student's string input against the answer text
                if (answerText.equals(studentValue)) {
                    System.out.println("Found matching answer: '" + answer.getText() + "', Correct: " + answer.getCorrect());
                    return answer.getCorrect();
                }
            }

            System.out.println("ERROR: No matching answer found for student input: '" + studentValue + "'");
            return false;
        } catch (Exception e) {
            System.out.println("ERROR: Exception in TRUE_FALSE evaluation: " + e.getMessage());
            return false;
        }
    }

    private boolean evaluateComplexAnswer(Question question, Object studentAnswer) {
        if (!(studentAnswer instanceof String)) return false;

        try {
            Map<String, String> answerMap = parseComplexAnswer((String) studentAnswer);

            if (question.getQuestionType() == QuestionType.CATEGORIZATION) {
                return evaluateCategorizationAnswer(question, answerMap);
            }

            if (question.getQuestionType() == QuestionType.MATCHING) {
                return evaluateMatchingAnswer(question, answerMap);
            }

        } catch (Exception e) {
            return false;
        }

        return false;
    }


    private Map<String, String> parseComplexAnswer(String answerJson) throws Exception {
        return objectMapper.readValue(answerJson, new TypeReference<Map<String, String>>() {});
    }

    private boolean evaluateCategorizationAnswer(Question question, Object studentAnswer) {
        try {
            Map<String, String> studentAnswers;

            // Handle different input types
            if (studentAnswer instanceof Map) {
                studentAnswers = convertMapToStringMap((Map<?, ?>) studentAnswer);
            } else if (studentAnswer instanceof String) {
                studentAnswers = parseComplexAnswerFromString((String) studentAnswer);
            } else {
                return false;
            }

            // Build correct answers map
            Map<String, String> correctAnswers = new HashMap<>();
            for (Answer answer : question.getAnswers()) {
                correctAnswers.put(answer.getText(), answer.getCategory());
            }

            // Compare each categorization
            for (Map.Entry<String, String> entry : studentAnswers.entrySet()) {
                String item = entry.getKey();
                String studentCategory = entry.getValue();
                String correctCategory = correctAnswers.get(item);

                if (!Objects.equals(studentCategory, correctCategory)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private double evaluateCategorizationAnswerPartial(Question question, Object studentAnswer) {
        try {
            System.out.println("Evaluating CATEGORIZATION answer with partial scoring...");

            // Validate input parameters
            if (question == null) {
                System.out.println("ERROR: Question is null");
                return 0.0;
            }

            if (studentAnswer == null) {
                System.out.println("WARNING: Student provided no answer for CATEGORIZATION question");
                return 0.0;
            }

            Map<String, String> studentAnswers;

            // Handle different input types with enhanced validation
            if (studentAnswer instanceof Map) {
                studentAnswers = convertMapToStringMap((Map<?, ?>) studentAnswer);
            } else if (studentAnswer instanceof String) {
                try {
                    studentAnswers = parseComplexAnswerFromString((String) studentAnswer);
                } catch (Exception parseException) {
                    System.out.println("ERROR: Failed to parse student answer JSON: " + parseException.getMessage());
                    return 0.0;
                }
            } else {
                System.out.println("ERROR: Unsupported student answer type for CATEGORIZATION: " + studentAnswer.getClass().getSimpleName());
                return 0.0;
            }

            // Validate that we have answers from the question
            if (question.getAnswers() == null || question.getAnswers().isEmpty()) {
                System.out.println("ERROR: Question has no answers defined");
                return 0.0;
            }

            // Build correct categories map with validation
            Map<String, String> correctCategories = new HashMap<>();
            for (Answer answer : question.getAnswers()) {
                if (answer.getText() != null && !answer.getText().trim().isEmpty() &&
                    answer.getCategory() != null && !answer.getCategory().trim().isEmpty()) {
                    correctCategories.put(answer.getText().trim(), answer.getCategory().trim());
                } else {
                    System.out.println("WARNING: Skipping invalid answer - text: '" + answer.getText() + "', category: '" + answer.getCategory() + "'");
                }
            }

            if (correctCategories.isEmpty()) {
                System.out.println("ERROR: No valid categorization items found in question");
                return 0.0;
            }

            System.out.println("Correct categories: " + correctCategories);
            System.out.println("Student answers: " + studentAnswers);

            // Enhanced scoring logic
            int correctCount = 0;
            int totalItems = correctCategories.size();
            int attemptedItems = 0;

            for (Map.Entry<String, String> correctEntry : correctCategories.entrySet()) {
                String item = correctEntry.getKey();
                String correctCategory = correctEntry.getValue();
                String studentCategory = studentAnswers.get(item);

                if (studentCategory != null && !studentCategory.trim().isEmpty()) {
                    attemptedItems++;
                    studentCategory = studentCategory.trim();

                    if (Objects.equals(studentCategory, correctCategory)) {
                        correctCount++;
                        System.out.println("✓ Correct categorization: '" + item + "' → '" + studentCategory + "'");
                    } else {
                        System.out.println("✗ Incorrect categorization: '" + item + "' → '" + studentCategory + "' (should be '" + correctCategory + "')");
                    }
                } else {
                    System.out.println("○ Not attempted: '" + item + "' (correct category: '" + correctCategory + "')");
                }
            }

            // Check for extra answers (items that don't exist in the question)
            for (String studentItem : studentAnswers.keySet()) {
                if (!correctCategories.containsKey(studentItem)) {
                    System.out.println("WARNING: Student answered for unknown item: '" + studentItem + "'");
                }
            }

            // Calculate percentage - only based on total items, not attempted items
            double percentage = totalItems > 0 ? (double) correctCount / totalItems : 0.0;

            // Ensure percentage is between 0.0 and 1.0
            percentage = Math.max(0.0, Math.min(1.0, percentage));

            System.out.println("CATEGORIZATION scoring summary:");
            System.out.println("  Total items: " + totalItems);
            System.out.println("  Attempted items: " + attemptedItems);
            System.out.println("  Correct items: " + correctCount);
            System.out.println("  Partial score: " + correctCount + "/" + totalItems + " = " + String.format("%.4f", percentage) + " (" + String.format("%.1f", percentage * 100) + "%)");

            return percentage;
        } catch (Exception e) {
            System.out.println("ERROR: Exception in CATEGORIZATION partial evaluation: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    private boolean evaluateMatchingAnswer(Question question, Object studentAnswer) {
        try {
            Map<String, String> studentAnswers;

            // Handle different input types
            if (studentAnswer instanceof Map) {
                studentAnswers = convertMapToStringMap((Map<?, ?>) studentAnswer);
            } else if (studentAnswer instanceof String) {
                studentAnswers = parseComplexAnswerFromString((String) studentAnswer);
            } else {
                return false;
            }

            // Build correct matches map
            Map<String, String> correctMatches = new HashMap<>();
            for (MatchingPair pair : question.getMatchingPairs()) {
                correctMatches.put(pair.getLeftItem(), pair.getRightItem());
            }

            // Check if all matches are correct
            if (studentAnswers.size() != correctMatches.size()) {
                return false;
            }

            for (Map.Entry<String, String> entry : studentAnswers.entrySet()) {
                String leftItem = entry.getKey();
                String studentRightItem = entry.getValue();
                String correctRightItem = correctMatches.get(leftItem);

                if (correctRightItem == null || !Objects.equals(studentRightItem, correctRightItem)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private double evaluateMatchingAnswerPartial(Question question, Object studentAnswer) {
        try {
            System.out.println("Evaluating MATCHING answer with partial scoring...");

            // Validate input parameters
            if (question == null) {
                System.out.println("ERROR: Question is null");
                return 0.0;
            }

            if (studentAnswer == null) {
                System.out.println("WARNING: Student provided no answer for MATCHING question");
                return 0.0;
            }

            Map<String, String> studentAnswers;

            // Handle different input types with enhanced validation
            if (studentAnswer instanceof Map) {
                studentAnswers = convertMapToStringMap((Map<?, ?>) studentAnswer);
            } else if (studentAnswer instanceof String) {
                try {
                    studentAnswers = parseComplexAnswerFromString((String) studentAnswer);
                } catch (Exception parseException) {
                    System.out.println("ERROR: Failed to parse student answer JSON: " + parseException.getMessage());
                    return 0.0;
                }
            } else {
                System.out.println("ERROR: Unsupported student answer type for MATCHING: " + studentAnswer.getClass().getSimpleName());
                return 0.0;
            }

            // Validate that we have matching pairs from the question
            if (question.getMatchingPairs() == null || question.getMatchingPairs().isEmpty()) {
                System.out.println("ERROR: Question has no matching pairs defined");
                return 0.0;
            }

            // Build correct matches map with validation
            Map<String, String> correctMatches = new HashMap<>();
            for (MatchingPair pair : question.getMatchingPairs()) {
                if (pair.getLeftItem() != null && !pair.getLeftItem().trim().isEmpty() &&
                    pair.getRightItem() != null && !pair.getRightItem().trim().isEmpty()) {
                    correctMatches.put(pair.getLeftItem().trim(), pair.getRightItem().trim());
                } else {
                    System.out.println("WARNING: Skipping invalid matching pair - left: '" + pair.getLeftItem() + "', right: '" + pair.getRightItem() + "'");
                }
            }

            if (correctMatches.isEmpty()) {
                System.out.println("ERROR: No valid matching pairs found in question");
                return 0.0;
            }

            System.out.println("Correct matches: " + correctMatches);
            System.out.println("Student answers: " + studentAnswers);

            // Enhanced scoring logic
            int correctCount = 0;
            int totalPairs = correctMatches.size();
            int attemptedPairs = 0;

            // Check each required left item
            for (Map.Entry<String, String> correctEntry : correctMatches.entrySet()) {
                String leftItem = correctEntry.getKey();
                String correctRightItem = correctEntry.getValue();
                String studentRightItem = studentAnswers.get(leftItem);

                if (studentRightItem != null && !studentRightItem.trim().isEmpty()) {
                    attemptedPairs++;
                    studentRightItem = studentRightItem.trim();

                    if (Objects.equals(studentRightItem, correctRightItem)) {
                        correctCount++;
                        System.out.println("✓ Correct match: '" + leftItem + "' → '" + studentRightItem + "'");
                    } else {
                        System.out.println("✗ Incorrect match: '" + leftItem + "' → '" + studentRightItem + "' (should be '" + correctRightItem + "')");
                    }
                } else {
                    System.out.println("○ Not attempted: '" + leftItem + "' (should match '" + correctRightItem + "')");
                }
            }

            // Check for extra answers (left items that don't exist in the question)
            for (String studentLeftItem : studentAnswers.keySet()) {
                if (!correctMatches.containsKey(studentLeftItem)) {
                    System.out.println("WARNING: Student provided match for unknown left item: '" + studentLeftItem + "'");
                }
            }

            // Calculate percentage - based on total pairs, not attempted pairs
            double percentage = totalPairs > 0 ? (double) correctCount / totalPairs : 0.0;

            // Ensure percentage is between 0.0 and 1.0
            percentage = Math.max(0.0, Math.min(1.0, percentage));

            System.out.println("MATCHING scoring summary:");
            System.out.println("  Total pairs: " + totalPairs);
            System.out.println("  Attempted pairs: " + attemptedPairs);
            System.out.println("  Correct pairs: " + correctCount);
            System.out.println("  Partial score: " + correctCount + "/" + totalPairs + " = " + String.format("%.4f", percentage) + " (" + String.format("%.1f", percentage * 100) + "%)");

            return percentage;
        } catch (Exception e) {
            System.out.println("ERROR: Exception in MATCHING partial evaluation: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }
    private boolean evaluateFillBlankAnswer(Question question, Object studentAnswer) {
        try {
            // Student answers come as an array for fill-in-the-blank questions
            List<String> studentAnswers;
            if (studentAnswer instanceof List) {
                studentAnswers = ((List<?>) studentAnswer).stream()
                        .map(Object::toString)
                        .map(String::trim)
                        .collect(Collectors.toList());
            } else if (studentAnswer instanceof String[]) {
                studentAnswers = Arrays.stream((String[]) studentAnswer)
                        .map(String::trim)
                        .collect(Collectors.toList());
            } else {
                // Fallback for single string (shouldn't happen in normal flow)
                studentAnswers = Arrays.asList(studentAnswer.toString().trim());
            }

            // Use BlankAnswer entities for validation
            List<BlankAnswer> blankAnswers = question.getBlankAnswers();
            if (blankAnswers.isEmpty()) {
                return false; // No blanks defined
            }

            // Sort blank answers by index to ensure proper order
            blankAnswers.sort(Comparator.comparing(BlankAnswer::getBlankIndex));

            // Check each blank answer
            for (int i = 0; i < blankAnswers.size(); i++) {
                BlankAnswer blankAnswer = blankAnswers.get(i);

                // Get student's answer for this blank
                String studentBlankAnswer = (i < studentAnswers.size())
                    ? studentAnswers.get(i)
                    : "";

                if (studentBlankAnswer.isEmpty()) {
                    return false; // Empty answer for required blank
                }

                // Check if student answer matches this blank
                if (!isBlankAnswerCorrect(blankAnswer, studentBlankAnswer)) {
                    return false; // At least one blank is wrong
                }
            }

            return true; // All blanks are correct

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlankAnswerCorrect(BlankAnswer blankAnswer, String studentAnswer) {
        try {
            // Check against correct answer
            String correctAnswer = blankAnswer.getCorrectAnswer();
            if (correctAnswer != null) {
                if (blankAnswer.getCaseSensitive()) {
                    if (correctAnswer.equals(studentAnswer)) {
                        return true;
                    }
                } else {
                    if (correctAnswer.equalsIgnoreCase(studentAnswer)) {
                        return true;
                    }
                }
            }

            // Check against acceptable answers
            String acceptableAnswersJson = blankAnswer.getAcceptableAnswers();
            if (acceptableAnswersJson != null && !acceptableAnswersJson.trim().isEmpty()) {
                try {
                    // Parse JSON array of acceptable answers
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    List<String> acceptableAnswers = mapper.readValue(acceptableAnswersJson,
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));

                    for (String acceptable : acceptableAnswers) {
                        if (blankAnswer.getCaseSensitive()) {
                            if (acceptable.equals(studentAnswer)) {
                                return true;
                            }
                        } else {
                            if (acceptable.equalsIgnoreCase(studentAnswer)) {
                                return true;
                            }
                        }
                    }
                } catch (Exception jsonException) {
                    // If JSON parsing fails, treat as comma-separated string
                    String[] acceptableAnswers = acceptableAnswersJson.split(",");
                    for (String acceptable : acceptableAnswers) {
                        acceptable = acceptable.trim();
                        if (blankAnswer.getCaseSensitive()) {
                            if (acceptable.equals(studentAnswer)) {
                                return true;
                            }
                        } else {
                            if (acceptable.equalsIgnoreCase(studentAnswer)) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateShortAnswer(Question question, Object studentAnswer) {
        try {
            // Get the correct answer from the question
            String correctAnswer = question.getCorrectAnswer();
            if (correctAnswer == null || correctAnswer.trim().isEmpty()) {
                return false; // No correct answer defined
            }

            // Get the student's answer as string
            String studentAnswerStr;
            if (studentAnswer instanceof String) {
                studentAnswerStr = (String) studentAnswer;
            } else {
                studentAnswerStr = studentAnswer.toString();
            }

            if (studentAnswerStr == null || studentAnswerStr.trim().isEmpty()) {
                return false; // Empty student answer
            }

            // Compare answers (case-insensitive, trimmed)
            return correctAnswer.trim().equalsIgnoreCase(studentAnswerStr.trim());

        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> convertMapToStringMap(Map<?, ?> inputMap) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            result.put(key, value);
        }
        return result;
    }
    private Map<String, String> parseComplexAnswerFromString(String answerJson) throws Exception {
        return objectMapper.readValue(answerJson, new TypeReference<Map<String, String>>() {});
    }
    private int[] calculateScore(Exam exam, String answersJson) {
        List<Question> questions = questionRepository.findByExamOrderById(exam);
        int totalPoints = 0;
        int earnedPoints = 0;

        System.out.println("=== EXAM SCORING DEBUG ===");
        System.out.println("Exam ID: " + exam.getId());
        System.out.println("Exam Title: " + exam.getTitle());
        System.out.println("Questions Count: " + questions.size());
        System.out.println("Raw Answers JSON: " + answersJson);

        // Parse JSON answers
        Map<String, Object> answers = parseAnswersJson(answersJson);
        System.out.println("Parsed Answers Map: " + answers);
        System.out.println("Parsed Answers Size: " + answers.size());

        for (Question question : questions) {
            int questionPoints = question.getPoints();
            totalPoints += questionPoints;

            String questionId = question.getId().toString();
            Object studentAnswer = answers.get(questionId);

            System.out.println("--- Question " + questionId + " ---");
            System.out.println("Question Text: " + question.getText());
            System.out.println("Question Type: " + question.getQuestionType());
            System.out.println("Question Points: " + questionPoints);
            System.out.println("Student Answer: " + studentAnswer);

            if (studentAnswer != null) {
                Object evaluationResult = evaluateAnswerWithPartialScoring(question, studentAnswer);

                if (evaluationResult instanceof Boolean) {
                    // Binary scoring (TRUE/FALSE, MULTIPLE_CHOICE, FILL_IN_THE_BLANKS, SHORT_ANSWER, etc.)
                    boolean isCorrect = (Boolean) evaluationResult;
                    System.out.println("Answer Evaluation: " + (isCorrect ? "CORRECT" : "INCORRECT"));
                    if (isCorrect) {
                        earnedPoints += questionPoints;
                        System.out.println("Points Awarded: " + questionPoints);
                    } else {
                        System.out.println("Points Awarded: 0");
                    }
                } else if (evaluationResult instanceof Double) {
                    // Partial scoring (MATCHING, CATEGORIZATION)
                    double percentage = (Double) evaluationResult;

                    // Apply scoring policy from the question
                    ScoringPolicy policy = question.getScoringPolicy();
                    int partialPoints = applyScoring(percentage, questionPoints, policy);

                    earnedPoints += partialPoints;
                    System.out.println("Answer Evaluation: PARTIAL (" + String.format("%.2f", percentage * 100) + "%)");
                    System.out.println("Question scoring policy: " + (policy != null ? policy : "DEFAULT"));
                    System.out.println("Points Awarded: " + partialPoints + " out of " + questionPoints);
                } else {
                    System.out.println("ERROR: Unexpected evaluation result type: " + evaluationResult.getClass().getSimpleName());
                    System.out.println("Points Awarded: 0");
                }
            } else {
                System.out.println("No answer provided - Points Awarded: 0");
            }
        }

        System.out.println("=== FINAL SCORE CALCULATION ===");
        System.out.println("Total Possible Points: " + totalPoints);
        System.out.println("Earned Points: " + earnedPoints);
        System.out.println("Percentage: " + (totalPoints > 0 ? (earnedPoints * 100.0 / totalPoints) : 0) + "%");
        System.out.println("Passing Score: " + exam.getPassingScore());
        System.out.println("Will Pass: " + (earnedPoints >= exam.getPassingScore()));
        System.out.println("=== END SCORING DEBUG ===");

        return new int[]{earnedPoints, totalPoints};
    }

    public Map<String, Object> evaluateStudentAnswer(Question question, Object studentAnswer) {
        Map<String, Object> result = new HashMap<>();

        Object evaluationResult = evaluateAnswerWithPartialScoring(question, studentAnswer);

        int earnedPoints;
        boolean isCorrect;

        if (evaluationResult instanceof Boolean) {
            // Binary scoring (TRUE/FALSE, MULTIPLE_CHOICE, FILL_IN_THE_BLANKS, SHORT_ANSWER, etc.)
            isCorrect = (Boolean) evaluationResult;
            earnedPoints = isCorrect ? question.getPoints() : 0;
        } else if (evaluationResult instanceof Double) {
            // Partial scoring (MATCHING, CATEGORIZATION)
            double percentage = (Double) evaluationResult;
            ScoringPolicy policy = question.getScoringPolicy();
            earnedPoints = applyScoring(percentage, question.getPoints(), policy);
            isCorrect = percentage >= 1.0; // Only consider "correct" if 100% accurate
        } else {
            // Fallback case
            isCorrect = false;
            earnedPoints = 0;
        }

        result.put("isCorrect", isCorrect);
        result.put("earnedPoints", earnedPoints);
        result.put("totalPoints", question.getPoints());

        // Add correct answer info based on question type
        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                Optional<Answer> correctAnswer = question.getAnswers().stream()
                        .filter(Answer::getCorrect)
                        .findFirst();
                result.put("correctAnswer", correctAnswer.map(Answer::getId).orElse(null));
                break;

            case CATEGORIZATION:
                Map<String, String> correctCategories = new HashMap<>();
                for (Answer answer : question.getAnswers()) {
                    correctCategories.put(answer.getText(), answer.getCategory());
                }
                result.put("correctAnswer", correctCategories);
                break;

            case MATCHING:
                Map<String, String> correctMatches = new HashMap<>();
                for (MatchingPair pair : question.getMatchingPairs()) {
                    correctMatches.put(pair.getLeftItem(), pair.getRightItem());
                }
                result.put("correctAnswer", correctMatches);
                break;

            case FILL_IN_THE_BLANKS:
                List<String> correctAnswers = question.getAnswers().stream()
                        .map(Answer::getText)
                        .collect(Collectors.toList());
                result.put("correctAnswer", correctAnswers);
                break;
        }

        return result;
    }
    @Transactional
    public Exam updateExam(Long examId, Exam examData, User teacher) {
        Exam existingExam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));

        // Security check: verify teacher owns the exam
        if (!existingExam.getLesson().getCourse().getTeacher().getId().equals(teacher.getId())) {
            throw new RuntimeException("Access denied: You can only update your own exams");
        }

        // Business rule: only allow updating if exam is still in draft status
        if (!existingExam.canBeModified()) {
            throw new RuntimeException("Cannot update finalized exam");
        }

        // Update exam fields
        existingExam.setTitle(examData.getTitle());
        existingExam.setDescription(examData.getDescription());
        existingExam.setTimeLimit(examData.getTimeLimit());
        existingExam.setPassingScore(examData.getPassingScore());
        existingExam.setAvailableFrom(examData.getAvailableFrom());
        existingExam.setAvailableTo(examData.getAvailableTo());

        return examRepository.save(existingExam);
    }

    @Transactional
    public Exam createExam(Exam exam, Long lessonId, boolean forceReplace) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // Security check: verify teacher owns the lesson
        User currentUser = getCurrentUser();
        if (!lesson.getCourse().getTeacher().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Unauthorized: You can only create exams for your own lessons");
        }

        // بررسی وجود آزمون قبلی
        if (lesson.getExam() != null) {
            Exam existingExam = lesson.getExam();
            List<Submission> submissions = submissionRepository.findByExam(existingExam);

            if (!submissions.isEmpty() && !forceReplace) {
                throw new RuntimeException("EXAM_EXISTS_WITH_SUBMISSIONS:" + submissions.size());
            }

            if (forceReplace) {
                // حذف تمام submissions
                submissionRepository.deleteByExam(existingExam);
                // حذف آزمون قبلی
                examRepository.delete(existingExam);
                lesson.setExam(null);
                lessonRepository.save(lesson);
            }
        }

        // Set exam properties
        exam.setStatus(ExamStatus.DRAFT);

        // STEP 1: Save exam first (without lesson relationship)
        Exam savedExam = examRepository.save(exam);

        // STEP 2: Set bidirectional relationship
        lesson.setExam(savedExam);
        savedExam.setLesson(lesson);

        // STEP 3: Save lesson to persist the relationship
        lessonRepository.save(lesson);

        return savedExam;
    }

    // Overload برای backward compatibility
    @Transactional
    public Exam createExam(Exam exam, Long lessonId) {
        return createExam(exam, lessonId, false);
    }

    // Helper method to get current user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userService.findByUsername(authentication.getName());
    }

    /**
     * Apply scoring policy to calculate final points from percentage and total points
     */
    public int applyScoring(double percentage, int questionPoints, ScoringPolicy policy) {
        if (policy == null) {
            policy = ScoringPolicy.ROUND_STANDARD;
        }

        // Use BigDecimal for precise calculation
        BigDecimal percentageBD = BigDecimal.valueOf(percentage);
        BigDecimal questionPointsBD = BigDecimal.valueOf(questionPoints);
        BigDecimal exactPoints = percentageBD.multiply(questionPointsBD);

        int finalPoints;
        switch (policy) {
            case ROUND_UP:
                finalPoints = exactPoints.setScale(0, RoundingMode.CEILING).intValue();
                break;
            case ROUND_DOWN:
                finalPoints = exactPoints.setScale(0, RoundingMode.FLOOR).intValue();
                break;
            case EXACT_DECIMAL:
                // For now, we'll still return integer since the system expects it
                // In future, this could be enhanced to support decimal scoring
                finalPoints = exactPoints.setScale(0, RoundingMode.HALF_UP).intValue();
                break;
            case ROUND_STANDARD:
            default:
                finalPoints = exactPoints.setScale(0, RoundingMode.HALF_UP).intValue();
                break;
        }

        // Ensure we don't exceed the question's total points
        finalPoints = Math.max(0, Math.min(finalPoints, questionPoints));

        System.out.println("Scoring policy applied: " + policy +
                          " | Exact: " + exactPoints.setScale(3, RoundingMode.HALF_UP) +
                          " → Final: " + finalPoints);

        return finalPoints;
    }

    /**
     * Recalculate submission score including both automatic and manual grades
     */
    public int recalculateSubmissionScore(Submission submission, Map<String, Object> manualGrades) {
        Exam exam = submission.getExam();
        List<Question> questions = questionRepository.findByExamOrderById(exam);

        // Parse student answers from JSON
        Map<String, Object> studentAnswers = parseAnswersJson(submission.getAnswersJson());

        int totalScore = 0;

        System.out.println("=== RECALCULATING SUBMISSION SCORE ===");
        System.out.println("Submission ID: " + submission.getId());
        System.out.println("Exam: " + exam.getTitle());
        System.out.println("Questions Count: " + questions.size());

        for (Question question : questions) {
            String questionId = String.valueOf(question.getId());
            int questionPoints = question.getPoints();
            int earnedPoints = 0;

            System.out.println("--- Question " + questionId + " (" + question.getQuestionType() + ") ---");
            System.out.println("Question Points: " + questionPoints);

            if (question.getQuestionType() == QuestionType.ESSAY ||
                question.getQuestionType() == QuestionType.SHORT_ANSWER) {
                // Use manual grade for essay and short answer questions
                if (manualGrades != null && manualGrades.containsKey(questionId)) {
                    earnedPoints = ((Number) manualGrades.get(questionId)).intValue();
                    // Ensure manual grade doesn't exceed question points
                    earnedPoints = Math.max(0, Math.min(earnedPoints, questionPoints));
                    System.out.println("Manual Grade: " + earnedPoints + "/" + questionPoints);
                } else {
                    // No manual grade assigned yet, use 0
                    earnedPoints = 0;
                    System.out.println("No manual grade assigned - Points: 0/" + questionPoints);
                }
            } else {
                // Use automatic grading for other question types
                Object studentAnswer = studentAnswers.get(questionId);
                if (studentAnswer != null) {
                    Object evaluationResult = evaluateAnswerWithPartialScoring(question, studentAnswer);

                    if (evaluationResult instanceof Boolean) {
                        // Binary scoring
                        boolean isCorrect = (Boolean) evaluationResult;
                        earnedPoints = isCorrect ? questionPoints : 0;
                        System.out.println("Auto Grade (Binary): " + earnedPoints + "/" + questionPoints + " (Correct: " + isCorrect + ")");
                    } else if (evaluationResult instanceof Double) {
                        // Partial scoring
                        double percentage = (Double) evaluationResult;
                        ScoringPolicy policy = question.getScoringPolicy();
                        earnedPoints = applyScoring(percentage, questionPoints, policy);
                        System.out.println("Auto Grade (Partial): " + earnedPoints + "/" + questionPoints + " (" + String.format("%.1f", percentage * 100) + "%)");
                    }
                } else {
                    System.out.println("No student answer - Points: 0/" + questionPoints);
                }
            }

            totalScore += earnedPoints;
        }

        System.out.println("=== RECALCULATION COMPLETE ===");
        System.out.println("Total Score: " + totalScore);
        System.out.println("===========================");

        return totalScore;
    }

}