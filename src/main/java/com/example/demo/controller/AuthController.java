package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication API endpoints for user registration and login")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Register a new student", description = "Create a new student account with role ROLE_STUDENT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Student registered successfully",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    @PostMapping("/register/student")
    public ResponseEntity<?> registerStudent(@RequestBody User user) {
        User savedUser = userService.registerUser(user, false); // false = not a teacher
        return ResponseEntity.ok("Student registered successfully");
    }

    @Operation(summary = "Register a new teacher", description = "Create a new teacher account with role ROLE_TEACHER")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Teacher registered successfully",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    @PostMapping("/register/teacher")
    public ResponseEntity<?> registerTeacher(@RequestBody User user) {
        User savedUser = userService.registerUser(user, true); // true = is a teacher
        return ResponseEntity.ok("Teacher registered successfully");
    }
}