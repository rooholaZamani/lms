package com.example.demo.controller;

import com.example.demo.dto.CourseDTO;
import com.example.demo.dto.UserDTO;
import com.example.demo.model.Course;
import com.example.demo.model.User;
import com.example.demo.service.CourseService;
import com.example.demo.service.DTOMapperService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Admin-only operations")
public class AdminController {

    private final UserService userService;
    private final CourseService courseService;
    private final DTOMapperService dtoMapperService;

    public AdminController(
            UserService userService,
            CourseService courseService,
            DTOMapperService dtoMapperService) {
        this.userService = userService;
        this.courseService = courseService;
        this.dtoMapperService = dtoMapperService;
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Returns a list of all users in the system")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        List<UserDTO> userDTOs = dtoMapperService.mapToUserDTOList(users);
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/courses")
    @Operation(summary = "Get all courses", description = "Returns a list of all courses in the system")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<List<CourseDTO>> getAllCourses() {
        List<Course> courses = courseService.getAllCourses();
        List<CourseDTO> courseDTOs = dtoMapperService.mapToCourseDTOList(courses);
        return ResponseEntity.ok(courseDTOs);
    }

    // Remaining methods stay the same
    @PostMapping("/users/{userId}/reset-password")
    @Operation(summary = "Reset user password", description = "Reset password for a specific user")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> resetUserPassword(
            @PathVariable Long userId,
            @RequestParam("newPassword") String newPassword) {

        try {
            userService.resetPassword(userId, newPassword);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Password reset successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register admin user", description = "Create a new admin user")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> registerAdmin(@RequestBody User user) {
        try {
            User savedUser = userService.registerAdmin(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Admin registered successfully");
            response.put("userId", savedUser.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }
}