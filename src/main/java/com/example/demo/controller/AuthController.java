package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register/student")
    public ResponseEntity<?> registerStudent(@RequestBody User user) {
        try {
            User savedUser = userService.registerUser(user, false); // false = not a teacher

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Student registered successfully");
            response.put("userId", savedUser.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/register/teacher")
    public ResponseEntity<?> registerTeacher(@RequestBody User user) {
        try {
            User savedUser = userService.registerUser(user, true); // true = is a teacher

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Teacher registered successfully");
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