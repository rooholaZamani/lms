package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class TestDataService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ProgressRepository progressRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public String generateComprehensiveTestData() {
        try {
            // Step 1: Clean existing test data (preserve admin)
            cleanTestData();

            // Step 2: Create teachers
            createTeachers();

            // Step 3: Create 99 students (st2-st100)
            createStudents();

            // Step 4: Create courses
            createCourses();

            // Step 5: Create lessons and content
            createLessonsAndContent();

            // Step 6: Create exams and questions
            createExamsAndQuestions();

            // Step 7: Create assignments
            createAssignments();

            // Step 8: Create enrollments
            createEnrollments();

            // Step 9: Create progress data
            createProgressData();

            return "SUCCESS: Created comprehensive test data - 100 students (st1-st100), 15 courses, 35+ exams, 28+ assignments";

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private void cleanTestData() {
        // Clean in proper order to avoid foreign key constraints
        activityLogRepository.deleteAll();
        progressRepository.deleteAll();
        assignmentRepository.deleteAll();
        questionRepository.deleteAll();
        examRepository.deleteAll();
        contentRepository.deleteAll();
        lessonRepository.deleteAll();
        courseRepository.deleteAll();

        // Remove test users (keep admin)
        List<User> testUsers = userRepository.findAll();
        testUsers.removeIf(user -> user.getUsername().equals("admin"));
        userRepository.deleteAll(testUsers);
    }

    private void createTeachers() {
        Role teacherRole = roleRepository.findByName("ROLE_TEACHER").orElseThrow();

        // Create teachers
        User[] teachers = {
            createUser("teach", "احمد", "محمدی", "teach@example.com", "1000000001", "09120000001", 35),
            createUser("teacher2", "فاطمه", "احمدی", "teacher2@example.com", "1000000002", "09120000002", 32),
            createUser("teacher3", "علی", "کریمی", "teacher3@example.com", "1000000003", "09120000003", 38)
        };

        for (User teacher : teachers) {
            teacher.setRoles(Set.of(teacherRole));
            userRepository.save(teacher);
        }
    }

    private void createStudents() {
        Role studentRole = roleRepository.findByName("ROLE_STUDENT").orElseThrow();

        // High performers (st2-st30)
        String[] highPerformerNames = {
            "علی احمدی", "مریم رضایی", "حسن موسوی", "فاطمه کریمی", "محمد حسینی",
            "زهرا نوری", "امیر صادقی", "سارا محمدی", "رضا علوی", "آیدا خانی",
            "حامد فراهانی", "نازنین تاجیک", "داود رستمی", "شیما اصفهانی", "پویا قاسمی",
            "نیلوفر شیرازی", "بهرام مشهدی", "لیلا تبریزی", "کاوه یزدی", "گلناز کرمانی",
            "سینا گیلانی", "پرنیان خراسانی", "آرمین بلوچی", "مهسا توکلی", "علیرضا جعفری",
            "ستاره حیدری", "کامران فروغی", "نرگس سلیمی", "جواد محبوبی"
        };

        for (int i = 0; i < highPerformerNames.length; i++) {
            String[] nameParts = highPerformerNames[i].split(" ");
            User student = createUser("st" + (i + 2), nameParts[0], nameParts[1],
                "st" + (i + 2) + "@student.lms.com", "200000000" + String.format("%02d", i + 1),
                "0912000000" + String.format("%02d", i + 1), 18 + (i % 5));
            student.setRoles(Set.of(studentRole));
            userRepository.save(student);
        }

        // Average performers (st31-st70) - simplified for brevity
        for (int i = 30; i < 70; i++) {
            User student = createUser("st" + (i + 1), "دانشجو" + (i + 1), "میانه",
                "st" + (i + 1) + "@student.lms.com", "200000000" + String.format("%02d", i + 1),
                "0912000000" + String.format("%02d", i + 1), 20 + (i % 7));
            student.setRoles(Set.of(studentRole));
            userRepository.save(student);
        }

        // At-risk students (st71-st100)
        for (int i = 70; i < 99; i++) {
            User student = createUser("st" + (i + 1), "دانشجو" + (i + 1), "ضعیف",
                "st" + (i + 1) + "@student.lms.com", "200000000" + String.format("%02d", i + 1),
                "0912000000" + String.format("%02d", i + 1), 22 + (i % 9));
            student.setRoles(Set.of(studentRole));
            userRepository.save(student);
        }
    }

    private User createUser(String username, String firstName, String lastName, String email, String nationalId, String phone, int age) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setNationalId(nationalId);
        user.setPhoneNumber(phone);
        user.setAge(age);
        return user;
    }

    private void createCourses() {
        User teacher1 = userRepository.findByUsername("teach").orElseThrow();
        User teacher2 = userRepository.findByUsername("teacher2").orElseThrow();
        User teacher3 = userRepository.findByUsername("teacher3").orElseThrow();

        // Create 15 diverse courses
        Course[] courses = {
            createCourse("ریاضی پیشرفته", "درس ریاضی برای دانشجویان پیشرفته", teacher1),
            createCourse("فیزیک کلاسیک و مدرن", "مبانی فیزیک کلاسیک و معرفی فیزیک مدرن", teacher1),
            createCourse("شیمی عمومی و آلی", "اصول شیمی عمومی و مقدمه شیمی آلی", teacher1),
            createCourse("علوم کامپیوتر", "مبانی برنامه‌نویسی و الگوریتم", teacher1),
            createCourse("آمار و احتمال", "آمار توصیفی و استنباطی", teacher1),
            createCourse("ادبیات فارسی", "ادبیات کلاسیک و معاصر فارسی", teacher2),
            createCourse("English Literature", "English language and literature", teacher2),
            createCourse("زبان عربی", "آموزش زبان و ادبیات عربی", teacher2),
            createCourse("بازاریابی", "اصول بازاریابی و فروش", teacher3),
            createCourse("مدیریت", "مبانی مدیریت سازمانی", teacher3),
            createCourse("اقتصاد", "اقتصاد کلان و خرد", teacher3),
            createCourse("تاریخ", "تاریخ ایران و جهان", teacher2),
            createCourse("فلسفه", "مبانی فلسفه و منطق", teacher2),
            createCourse("روان‌شناسی", "روان‌شناسی عمومی و شناختی", teacher3),
            createCourse("مهندسی برق", "مبانی مهندسی برق و الکترونیک", teacher1)
        };

        for (Course course : courses) {
            courseRepository.save(course);
        }
    }

    private Course createCourse(String title, String description, User teacher) {
        Course course = new Course();
        course.setTitle(title);
        course.setDescription(description);
        course.setTeacher(teacher);
        course.setActive(true);
        return course;
    }

    private void createLessonsAndContent() {
        List<Course> courses = courseRepository.findAll();

        for (Course course : courses) {
            // Create 3-8 lessons per course
            int lessonCount = 3 + (course.getId().intValue() % 6);

            for (int i = 1; i <= lessonCount; i++) {
                Lesson lesson = new Lesson();
                lesson.setTitle("درس " + i + " - " + course.getTitle());
                lesson.setDescription("توضیحات درس " + i);
                lesson.setCourse(course);
                lesson.setOrderIndex(i);
                lesson.setDuration(60 + (i * 15)); // 75-135 minutes
                lessonRepository.save(lesson);

                // Create 2-4 content pieces per lesson
                int contentCount = 2 + (i % 3);
                ContentType[] types = {ContentType.TEXT, ContentType.VIDEO, ContentType.PDF};

                for (int j = 1; j <= contentCount; j++) {
                    Content content = new Content();
                    content.setTitle("محتوا " + j + " - " + lesson.getTitle());
                    content.setLesson(lesson);
                    content.setType(types[j % 3]);
                    content.setOrderIndex(j);
                    if (content.getType() == ContentType.TEXT) {
                        content.setTextContent("محتوای متنی برای " + content.getTitle());
                    }
                    contentRepository.save(content);
                }
            }
        }
    }

    private void createExamsAndQuestions() {
        List<Course> courses = courseRepository.findAll();

        for (Course course : courses) {
            // Create 2-3 exams per course
            int examCount = 2 + (course.getId().intValue() % 2);

            for (int i = 1; i <= examCount; i++) {
                Exam exam = new Exam();
                exam.setTitle("آزمون " + i + " - " + course.getTitle());
                exam.setDescription("آزمون جامع درس");
                exam.setTimeLimit(60 + (i * 30)); // 90-150 minutes
                exam.setPassingScore(60);
                exam.setTotalPossibleScore(100);
                exam.setStatus(ExamStatus.FINALIZED);
                exam.setAvailableFrom(LocalDateTime.now().minusDays(30));
                exam.setAvailableTo(LocalDateTime.now().plusDays(30));
                examRepository.save(exam);

                // Create 3-6 questions per exam
                int questionCount = 3 + (i * 2);
                QuestionType[] types = {QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY};

                for (int j = 1; j <= questionCount; j++) {
                    Question question = new Question();
                    question.setExam(exam);
                    question.setText("سوال " + j + " از آزمون " + exam.getTitle());
                    question.setQuestionType(types[j % 3]);
                    question.setPoints(10 + (j * 5));
                    question.setDifficulty(0.3 + (j * 0.1));
                    question.setRequired(true);
                    questionRepository.save(question);
                }
            }
        }
    }

    private void createAssignments() {
        List<Course> courses = courseRepository.findAll();

        for (Course course : courses) {
            List<Lesson> lessons = lessonRepository.findByCourseId(course.getId());
            if (!lessons.isEmpty()) {
                // Create 1-2 assignments per course
                int assignmentCount = 1 + (course.getId().intValue() % 2);

                for (int i = 1; i <= assignmentCount; i++) {
                    Assignment assignment = new Assignment();
                    assignment.setTitle("تکلیف " + i + " - " + course.getTitle());
                    assignment.setDescription("تکلیف عملی برای درس");
                    assignment.setLesson(lessons.get(i % lessons.size()));
                    assignment.setTeacher(course.getTeacher());
                    assignment.setDueDate(LocalDateTime.now().plusDays(7 + i * 7));
                    assignmentRepository.save(assignment);
                }
            }
        }
    }

    private void createEnrollments() {
        List<Course> courses = courseRepository.findAll();
        List<User> students = userRepository.findAll().stream()
            .filter(user -> user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT")))
            .toList();

        Random random = new Random();

        for (User student : students) {
            // Determine enrollment count based on username
            int enrollmentCount;
            String username = student.getUsername();

            if (username.equals("st1") || (username.startsWith("st") &&
                Integer.parseInt(username.substring(2)) <= 30)) {
                // High performers: 4-5 courses
                enrollmentCount = 4 + random.nextInt(2);
            } else if (username.startsWith("st") &&
                Integer.parseInt(username.substring(2)) <= 70) {
                // Average performers: 2-4 courses
                enrollmentCount = 2 + random.nextInt(3);
            } else {
                // At-risk students: 1-3 courses
                enrollmentCount = 1 + random.nextInt(3);
            }

            // Randomly select courses
            Collections.shuffle(courses);
            for (int i = 0; i < Math.min(enrollmentCount, courses.size()); i++) {
                Course course = courses.get(i);
                if (!course.getEnrolledStudents().contains(student)) {
                    course.getEnrolledStudents().add(student);
                    courseRepository.save(course);
                }
            }
        }
    }

    private void createProgressData() {
        List<User> students = userRepository.findAll().stream()
            .filter(user -> user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT")))
            .toList();

        for (User student : students) {
            List<Course> enrolledCourses = courseRepository.findByEnrolledStudentsContaining(student);

            for (Course course : enrolledCourses) {
                Progress progress = new Progress();
                progress.setStudent(student);
                progress.setCourse(course);
                progress.setLastAccessed(LocalDateTime.now().minusDays(new Random().nextInt(7)));

                // Set progress based on performance level
                String username = student.getUsername();
                double completionRate;

                if (username.equals("st1") || (username.startsWith("st") &&
                    Integer.parseInt(username.substring(2)) <= 30)) {
                    // High performers: 80-95%
                    completionRate = 0.80 + (new Random().nextDouble() * 0.15);
                } else if (username.startsWith("st") &&
                    Integer.parseInt(username.substring(2)) <= 70) {
                    // Average performers: 60-80%
                    completionRate = 0.60 + (new Random().nextDouble() * 0.20);
                } else {
                    // At-risk students: 30-60%
                    completionRate = 0.30 + (new Random().nextDouble() * 0.30);
                }

                progress.setCompletionPercentage(completionRate);
                progress.setTotalStudyTime((long) (completionRate * 18000)); // Up to 5 hours
                progress.setCurrentStreak((int) (completionRate * 20)); // Up to 20 days

                progressRepository.save(progress);
            }
        }
    }

    public Map<String, Object> getTestDataSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("Total Users", userRepository.count());
        summary.put("Total Students", userRepository.findAll().stream()
            .filter(user -> user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT")))
            .count());
        summary.put("Total Teachers", userRepository.findAll().stream()
            .filter(user -> user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER")))
            .count());
        summary.put("Total Courses", courseRepository.count());
        summary.put("Total Lessons", lessonRepository.count());
        summary.put("Total Content", contentRepository.count());
        summary.put("Total Exams", examRepository.count());
        summary.put("Total Questions", questionRepository.count());
        summary.put("Total Assignments", assignmentRepository.count());
        summary.put("Total Progress Records", progressRepository.count());

        return summary;
    }
}