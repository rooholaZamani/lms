package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.Progress;
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
        User user = userService.findByUsername(authentication.getName());

        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_TEACHER"))) {
            List<Course> courses = courseService.getTeacherCourses(user);
            model.addAttribute("courses", courses);
            return "teacher-dashboard";
        } else {
            List<Course> courses = courseService.getEnrolledCourses(user);
            List<Progress> progressList = progressService.getProgressByStudent(user);

            model.addAttribute("courses", courses);
            model.addAttribute("progressList", progressList);
            return "student-dashboard";
        }
    }

    @GetMapping("/courses/{courseId}")
    public String viewCourse(@PathVariable Long courseId, Authentication authentication, Model model) {
        User user = userService.findByUsername(authentication.getName());
        Course course = courseService.getCourseById(courseId);

        model.addAttribute("course", course);

        // For students, add progress information
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals("ROLE_STUDENT"))) {
            Progress progress = progressService.getOrCreateProgress(user, course);
            model.addAttribute("progress", progress);
        }

        return "course-details";
    }

    // Other view mappings as needed...
}