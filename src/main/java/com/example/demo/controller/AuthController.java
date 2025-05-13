package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register/student")
    public ResponseEntity<?> registerStudent(@RequestBody User user) {
        User savedUser = userService.registerUser(user, false); // false = not a teacher
        return ResponseEntity.ok("Student registered successfully");
    }

    @PostMapping("/register/teacher")
    public ResponseEntity<?> registerTeacher(@RequestBody User user) {
        User savedUser = userService.registerUser(user, true); // true = is a teacher
        return ResponseEntity.ok("Teacher registered successfully");
    }
}