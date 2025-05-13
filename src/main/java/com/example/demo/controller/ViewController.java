package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.ProgressService;
import com.example.demo.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@Controller
public class ViewController {

    private final UserService userService;
    private final CourseService courseService;
    private final ProgressService progressService;

    public ViewController(
            UserService userService,
            CourseService courseService,
            ProgressService progressService) {
        this.userService = userService;
        this.courseService = courseService;
        this.progressService = progressService;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName());
        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        if (isTeacher) {
            // داشبورد معلم
            List<Course> courses = courseService.getTeacherCourses(user);
            model.addAttribute("courses", courses);
            model.addAttribute("user", user);
            return "teacher-dashboard";
        } else {
            // داشبورد دانش‌آموز
            List<Course> courses = courseService.getEnrolledCourses(user);
            List<Progress> progressList = progressService.getProgressByStudent(user);

            // محاسبه آمار کلی برای نمایش در داشبورد
            int totalCourses = courses.size();
            int completedCourses = 0;
            double averageProgress = 0;

            if (!progressList.isEmpty()) {
                for (Progress progress : progressList) {
                    if (progress.getCompletionPercentage() >= 100) {
                        completedCourses++;
                    }
                    averageProgress += progress.getCompletionPercentage();
                }
                averageProgress = averageProgress / progressList.size();
            }

            model.addAttribute("user", user);
            model.addAttribute("courses", courses);
            model.addAttribute("progressList", progressList);
            model.addAttribute("totalCourses", totalCourses);
            model.addAttribute("completedCourses", completedCourses);
            model.addAttribute("averageProgress", (int)averageProgress);

            return "student-dashboard";
        }
    }

    @GetMapping("/courses/{courseId}")
    public String viewCourse(@PathVariable Long courseId, Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);

        model.addAttribute("course", course);
        model.addAttribute("user", user);

        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));
        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("isStudent", isStudent);

        // برای دانش‌آموزان، اطلاعات پیشرفت را اضافه کنید
        if (isStudent) {
            Progress progress = progressService.getOrCreateProgress(user, course);
            model.addAttribute("progress", progress);
        }

        return "course-details";
    }

    @GetMapping("/available-courses")
    public String availableCourses(Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName());
        List<Course> allCourses = courseService.getAllCourses();
        List<Course> enrolledCourses = courseService.getEnrolledCourses(user);

        // فیلتر کردن دوره‌های که دانش‌آموز هنوز ثبت‌نام نکرده است
        allCourses.removeAll(enrolledCourses);

        model.addAttribute("user", user);
        model.addAttribute("courses", allCourses);

        return "available-courses";
    }

    @GetMapping("/exams/{examId}")
    public String viewExam(@PathVariable Long examId, Authentication authentication, Model model) {
        if (authentication == null) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName());
        // منطق واقعی برای نمایش آزمون به دانش‌آموز یا معلم
        // ...

        model.addAttribute("user", user);
        return "exam-view";
    }
}