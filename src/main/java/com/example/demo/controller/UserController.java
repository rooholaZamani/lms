package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/current")
    public ResponseEntity<User> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByUsername(authentication.getName());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/role")
    public ResponseEntity<Map<String, Object>> getUserRole(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(authentication.getName());
        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        Map<String, Object> response = new HashMap<>();
        response.put("isTeacher", isTeacher);
        response.put("isStudent", !isTeacher);

        return ResponseEntity.ok(response);
    }
}