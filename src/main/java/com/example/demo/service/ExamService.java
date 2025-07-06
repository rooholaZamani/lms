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
@Service
public class ExamService {

    private final ExamRepository examRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ExamService(
            ExamRepository examRepository,
            LessonRepository lessonRepository,
            QuestionRepository questionRepository,
            SubmissionRepository submissionRepository, CourseRepository courseRepository) {
        this.examRepository = examRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Exam createExam(Exam exam, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // Set default status to DRAFT
        exam.setStatus(ExamStatus.DRAFT);

        exam.setLesson(lesson);
        
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

        submission.setScore(earnedPoints);
        submission.setPassed(earnedPoints >= exam.getPassingScore());

        return submissionRepository.save(submission);
    }


    private Map<String, Object> parseAnswersJson(String answersJson) {
        if (answersJson == null || answersJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(answersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
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
                case CATEGORIZATION:
                    // These use the answers field
                    if (!question.getAnswers().isEmpty()) {
                        // Check if at least one answer is marked as correct
                        hasValidAnswers = question.getAnswers().stream()
                                .anyMatch(Answer::getCorrect);
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
                type == QuestionType.FILL_IN_THE_BLANK;
    }
    public Exam findById(Long examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found with id: " + examId));
    }





    public boolean evaluateAnswer(Question question, Object studentAnswer) {
        if (studentAnswer == null) return false;

        switch (question.getQuestionType()) {
            case MULTIPLE_CHOICE:
            case TRUE_FALSE:
                return evaluateSimpleAnswer(question, studentAnswer);
            case CATEGORIZATION:
                return evaluateCategorizationAnswer(question, studentAnswer);
            case MATCHING:
                return evaluateMatchingAnswer(question, studentAnswer);
            case FILL_IN_THE_BLANK:
            case FILL_IN_THE_BLANKS:
                return evaluateFillBlankAnswer(question, studentAnswer);
            default:
                return false;
        }
    }
    private boolean evaluateSimpleAnswer(Question question, Object studentAnswer) {
        try {
            Long answerId;
            if (studentAnswer instanceof Number) {
                answerId = ((Number) studentAnswer).longValue();
            } else if (studentAnswer instanceof String) {
                answerId = Long.parseLong((String) studentAnswer);
            } else {
                return false;
            }

            return question.getAnswers().stream()
                    .filter(answer -> answer.getId().equals(answerId))
                    .findFirst()
                    .map(Answer::getCorrect)
                    .orElse(false);
        } catch (NumberFormatException e) {
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
    private boolean evaluateFillBlankAnswer(Question question, Object studentAnswer) {
        try {
            String studentText = studentAnswer.toString().trim();

            // Check against all possible correct answers
            return question.getAnswers().stream()
                    .anyMatch(answer -> {
                        String correctText = answer.getText().trim();
                        // Case-insensitive comparison
                        return correctText.equalsIgnoreCase(studentText);
                    });

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

        // Parse JSON answers
        Map<String, Object> answers = parseAnswersJson(answersJson);

        for (Question question : questions) {
            totalPoints += question.getPoints();

            String questionId = question.getId().toString();
            Object studentAnswer = answers.get(questionId);

            if (studentAnswer != null) {
                boolean isCorrect = evaluateAnswer(question, studentAnswer);
                if (isCorrect) {
                    earnedPoints += question.getPoints();
                }
            }
        }

        return new int[]{earnedPoints, totalPoints};
    }

    public Map<String, Object> evaluateStudentAnswer(Question question, Object studentAnswer) {
        Map<String, Object> result = new HashMap<>();

        boolean isCorrect = evaluateAnswer(question, studentAnswer);
        int earnedPoints = isCorrect ? question.getPoints() : 0;

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

            case FILL_IN_THE_BLANK:
            case FILL_IN_THE_BLANKS:
                List<String> correctAnswers = question.getAnswers().stream()
                        .map(Answer::getText)
                        .collect(Collectors.toList());
                result.put("correctAnswer", correctAnswers);
                break;
        }

        return result;
    }

}