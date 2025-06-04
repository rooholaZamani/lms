package com.example.demo.controller;

import com.example.demo.dto.UserDTO;
import com.example.demo.model.User;
import com.example.demo.service.DTOMapperService;
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
    private final DTOMapperService dtoMapperService;

    public UserController(UserService userService, DTOMapperService dtoMapperService) {
        this.userService = userService;
        this.dtoMapperService = dtoMapperService;
    }

    @GetMapping("/current")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByUsername(authentication.getName());
        UserDTO userDTO = dtoMapperService.mapToUserDTO(user);
        return ResponseEntity.ok(userDTO);
    }

    @GetMapping("/role")
    public ResponseEntity<Map<String, Object>> getUserRole(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(authentication.getName());

        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));

        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));

        Map<String, Object> response = new HashMap<>();
        response.put("isTeacher", isTeacher);
        response.put("isStudent", isStudent);
        response.put("isAdmin", isAdmin);

        return ResponseEntity.ok(response);
    }
}