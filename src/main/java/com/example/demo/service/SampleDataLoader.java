package com.example.demo.service;

import com.example.demo.config.DataLoaderConfig;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class SampleDataLoader implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private ActivityLogRepository activityLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ContentRepository contentRepository;
    @Autowired
    private DataLoaderConfig dataLoaderConfig;

    private final Random random = new Random();
    
    @Override
    @Transactional
    public void run(String... args) {

        if (!dataLoaderConfig.isCreateSampleData()) {
            System.out.println("Sample data creation is disabled in configuration.");
            return;
        }
        // فقط اگر داده‌ای موجود نباشد، داده‌های نمونه ایجاد کن
        if (courseRepository.count() > 0) {
            System.out.println("Sample data already exists. Skipping data creation.");
            return;
        }
        
        System.out.println("Creating sample data...");
        createSampleData();
        System.out.println("Sample data created successfully!");
    }

    private void createSampleData() {
        // 1. ایجاد نقش‌ها
        Role teacherRole = createRoleIfNotExists("ROLE_TEACHER");
        Role studentRole = createRoleIfNotExists("ROLE_STUDENT");

        // 2. ایجاد معلم
        User teacher = createTeacher();

        // 3. ایجاد سه دوره
        List<Course> courses = createSampleCourses(teacher);

        // 4. برای هر دوره، ایجاد درس‌ها، دانش‌آموزان و آزمون‌ها
        for (Course course : courses) {
            List<User> students = createStudentsForCourse(course, 30);
            createLessonsForCourse(course);
            createExamsAndSubmissions(course, students);
        }
    }

    private Role createRoleIfNotExists(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(roleName);
                    return roleRepository.save(role);
                });
    }

    private User createTeacher() {
        User teacher = new User();
        teacher.setUsername("teacher1");
        teacher.setPassword(passwordEncoder.encode("123456"));
        teacher.setFirstName("احمد");
        teacher.setLastName("محمدی");
        teacher.setNationalId("1234567890");
        teacher.setEmail("teacher@example.com");
        teacher.setAge(35);
        teacher.getRoles().add(roleRepository.findByName("ROLE_TEACHER").get());
        return userRepository.save(teacher);
    }

    private List<Course> createSampleCourses(User teacher) {
        List<Course> courses = new ArrayList<>();
        
        String[] courseTitles = {
            "ریاضی پایه دهم",
            "فیزیک تجربی",
            "شیمی عمومی"
        };
        
        String[] courseDescriptions = {
            "آموزش مفاهیم پایه‌ای ریاضیات برای دانش‌آموزان دهم",
            "بررسی اصول و قوانین فیزیک به همراه آزمایش‌های عملی",
            "مطالعه عناصر، ترکیبات و واکنش‌های شیمیایی"
        };

        for (int i = 0; i < courseTitles.length; i++) {
            Course course = new Course();
            course.setTitle(courseTitles[i]);
            course.setDescription(courseDescriptions[i]);
            course.setTeacher(teacher);
            course.setActive(true);
            courses.add(courseRepository.save(course));
        }
        
        return courses;
    }

    private List<User> createStudentsForCourse(Course course, int studentCount) {
        List<User> students = new ArrayList<>();
        Role studentRole = roleRepository.findByName("ROLE_STUDENT").get();
        
        String[] firstNames = {"علی", "فاطمه", "محمد", "زهرا", "حسن", "مریم", "رضا", "سارا", 
                              "امیر", "نرگس", "مجید", "طاهره", "حمید", "نازنین", "بهزاد"};
        String[] lastNames = {"احمدی", "محمدی", "حسینی", "رضایی", "کریمی", "نوری", "زارعی", 
                             "صادقی", "عباسی", "موسوی", "یوسفی", "ابراهیمی", "قاسمی", "جعفری", "اصغری"};

        for (int i = 0; i < studentCount; i++) {
            User student = new User();
            student.setUsername("student_" + course.getId() + "_" + (i + 1));
            student.setPassword(passwordEncoder.encode("123456"));
            student.setFirstName(firstNames[random.nextInt(firstNames.length)]);
            student.setLastName(lastNames[random.nextInt(lastNames.length)]);
            student.setNationalId(generateNationalId());
            student.setEmail("student" + (i + 1) + "@example.com");
            student.setAge(15 + random.nextInt(3)); // 15-17 سال
            student.getRoles().add(studentRole);
            
            student = userRepository.save(student);
            course.getEnrolledStudents().add(student);
            students.add(student);
        }
        
        courseRepository.save(course);
        return students;
    }

    private void createLessonsForCourse(Course course) {
        String[][] lessonData = {
            // ریاضی
            {"مجموعه‌ها و عملیات روی آن‌ها", "آشنایی با مفهوم مجموعه و انواع عملیات"},
            {"معادلات درجه اول", "حل معادلات خطی و کاربردهای آن"},
            {"نامساوی‌ها", "حل نامساوی‌های خطی و درجه دوم"},
            // فیزیک  
            {"کینماتیک", "مطالعه حرکت اجسام بدون در نظر گیری علت"},
            {"دینامیک", "بررسی نیروها و تأثیر آن‌ها بر حرکت"},
            {"انرژی و کار", "مفاهیم انرژی جنبشی و پتانسیل"},
            // شیمی
            {"ساختار اتم", "بررسی ساختار درونی اتم و مدل‌های اتمی"},
            {"جدول تناوبی", "آشنایی با عناصر و ویژگی‌های آن‌ها"},
            {"پیوند شیمیایی", "انواع پیوندها و تشکیل مولکول‌ها"}
        };

        int startIndex = getCourseStartIndex(course.getTitle());
        
        for (int i = 0; i < 3; i++) {
            Lesson lesson = new Lesson();
            lesson.setTitle(lessonData[startIndex + i][0]);
            lesson.setDescription(lessonData[startIndex + i][1]);
            lesson.setCourse(course);
            lesson.setOrderIndex(i + 1);
            lesson.setDuration(3600); // 1 ساعت
            lesson.setCreatedAt(LocalDateTime.now().minusDays(30 - i * 5));
            
            lesson = lessonRepository.save(lesson);
            
            // ایجاد محتوای متنی برای درس
            createTextContentForLesson(lesson);
        }
    }
    
    private int getCourseStartIndex(String courseTitle) {
        if (courseTitle.contains("ریاضی")) return 0;
        if (courseTitle.contains("فیزیک")) return 3;
        if (courseTitle.contains("شیمی")) return 6;
        return 0;
    }

    private void createTextContentForLesson(Lesson lesson) {
        Content content = new Content();
        content.setTitle("محتوای " + lesson.getTitle());
        content.setType(ContentType.TEXT);
        content.setLesson(lesson);
        content.setOrderIndex(1);
        content.setCreatedAt(LocalDateTime.now());
        
        // محتوای متنی نمونه
        String sampleText = generateSampleTextContent(lesson.getTitle());
        content.setTextContent(sampleText);
        
        contentRepository.save(content);
    }

    private String generateSampleTextContent(String lessonTitle) {
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("مجموعه‌ها و عملیات روی آن‌ها", 
            "مجموعه مفهومی بنیادی در ریاضیات است که به مجموعه‌ای از اشیاء مشخص گفته می‌شود...");
        contentMap.put("معادلات درجه اول", 
            "معادله خطی معادله‌ای است که بالاترین توان متغیر آن یک باشد...");
        contentMap.put("کینماتیک", 
            "کینماتیک شاخه‌ای از مکانیک است که حرکت اجسام را بدون در نظر گیری علت آن مطالعه می‌کند...");
        
        return contentMap.getOrDefault(lessonTitle, 
            "این درس شامل مطالب مهمی در زمینه " + lessonTitle + " می‌باشد که برای یادگیری ضروری است.");
    }

    private void createExamsAndSubmissions(Course course, List<User> students) {
        List<Lesson> lessons = lessonRepository.findByCourseOrderByOrderIndex(course);
        
        for (Lesson lesson : lessons) {
            // ایجاد آزمون برای درس
            Exam exam = createExamForLesson(lesson);
            
            // ایجاد submission برای هر دانش‌آموز
            for (User student : students) {
                createSubmissionForExam(exam, student);
                createActivityLogs(student, lesson, exam);
            }
        }
    }

    private Exam createExamForLesson(Lesson lesson) {
        Exam exam = new Exam();
        exam.setTitle("آزمون " + lesson.getTitle());
        exam.setDescription("ارزیابی یادگیری درس " + lesson.getTitle());
        exam.setTimeLimit(1800); // 30 دقیقه
        exam.setPassingScore(60);
        exam.setStatus(ExamStatus.FINALIZED);
        exam.setFinalizedAt(LocalDateTime.now().minusDays(20));
        exam.setLesson(lesson);
        exam.setTotalPossibleScore(100);
        
        exam = examRepository.save(exam);
        
        // ایجاد سوالات برای آزمون
        createQuestionsForExam(exam);
        
        return exam;
    }

    private void createQuestionsForExam(Exam exam) {
        for (int i = 1; i <= 10; i++) {
            Question question = new Question();
            question.setExam(exam);
            question.setText("سوال شماره " + i + " در زمینه " + exam.getLesson().getTitle());
            question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
            question.setPoints(10);
            
            question = questionRepository.save(question);
            
            // ایجاد گزینه‌ها
            for (int j = 1; j <= 4; j++) {
                Answer answer = new Answer();
                answer.setText("گزینه " + j);
                answer.setCorrect(j == 1); // گزینه اول صحیح
                answer.setPoints(j == 1 ? 10 : 0);
                question.getAnswers().add(answer);
            }
            
            questionRepository.save(question);
        }
    }

    private void createSubmissionForExam(Exam exam, User student) {
        Submission submission = new Submission();
        submission.setExam(exam);
        submission.setStudent(student);
        submission.setSubmissionTime(LocalDateTime.now().minusDays(random.nextInt(15)));
        
        // تولید نمره با توزیع نرمال (میانگین بین 50-100، انحراف معیار 20)
        double examMean = 50 + random.nextDouble() * 50; // میانگین بین 50-100
        int score = generateNormalScore(examMean, 20);
        submission.setScore(score);
        submission.setPassed(score >= exam.getPassingScore());
        
        // زمان صرف شده (متناسب با نمره)
        long timeSpent = generateStudyTime(score);
        submission.setTimeSpent(timeSpent);
        
        // JSON ساده برای پاسخ‌ها
        submission.setAnswersJson("{\"answers\": [1,2,1,1,3,1,2,1,4,1]}");
        
        submissionRepository.save(submission);
    }

    private void createActivityLogs(User student, Lesson lesson, Exam exam) {
        // فعالیت مطالعه (متناسب با نمره)
        Optional<Submission> submissionOptional = submissionRepository.findByStudentAndExam(student, exam);
        int studySessionCount = 3 + random.nextInt(5); // 3-7 جلسه مطالعه

        if (submissionOptional.isEmpty())
            return;

        Submission submission = submissionOptional.get();

        for (int i = 0; i < studySessionCount; i++) {
            ActivityLog studyLog = new ActivityLog();
            studyLog.setUser(student);
            studyLog.setActivityType("CONTENT_VIEW");
            studyLog.setRelatedEntityId(lesson.getId());
            studyLog.setTimestamp(LocalDateTime.now().minusDays(random.nextInt(30)));
            
            // زمان مطالعه متناسب با نمره
            long studyTime = generateStudyTime(submission.getScore()) / studySessionCount;
            studyLog.setTimeSpent(studyTime);
            
            // متادیتا
            studyLog.getMetadata().put("lessonTitle", lesson.getTitle());
            studyLog.getMetadata().put("courseId", lesson.getCourse().getId().toString());
            
            activityLogRepository.save(studyLog);
        }
        
        // فعالیت آزمون
        ActivityLog examLog = new ActivityLog();
        examLog.setUser(student);
        examLog.setActivityType("EXAM_SUBMISSION");
        examLog.setRelatedEntityId(submission.getId());
        examLog.setTimestamp(submission.getSubmissionTime());
        examLog.setTimeSpent(submission.getTimeSpent());
        
        examLog.getMetadata().put("examTitle", exam.getTitle());
        examLog.getMetadata().put("score", submission.getScore().toString());
        examLog.getMetadata().put("passed", String.valueOf(submission.isPassed()));
        
        activityLogRepository.save(examLog);
    }

    private int generateNormalScore(double mean, double stdDev) {
        // تولید نمره با توزیع نرمال
        double score = random.nextGaussian() * stdDev + mean;
        // محدود کردن بین 0 و 100
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    private long generateStudyTime(int score) {
        // زمان مطالعه متناسب با نمره (نمره بالاتر = مطالعه بیشتر)
        double basetime = 1800; // 30 دقیقه پایه
        double multiplier = 0.5 + (score / 100.0) * 2; // ضریب 0.5 تا 2.5
        return (long) (basetime * multiplier * (0.8 + random.nextDouble() * 0.4)); // تغییرات تصادفی
    }

    private String generateNationalId() {
        return String.format("%010d", random.nextInt(1000000000));
    }
}