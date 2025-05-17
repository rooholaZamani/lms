package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
//@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "API for user authentication and registration")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @Operation(
            summary = "User login",
            description = "Authenticate user and return session token",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Login successful",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Invalid credentials"
                    )
            }
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            // Extract credentials
            String username = loginRequest.getUsername();
            String password = loginRequest.getPassword();

            // Create authentication token
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            // Authenticate
            Authentication authentication = authenticationManager.authenticate(authToken);

            // Store authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Get user details
            User user = userService.findByUsername(username);

            // Create session if one doesn't exist
            HttpSession session = request.getSession(true);

            // Create response
            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .message("Login successful")
                    .userId(user.getId())
                    .username(user.getUsername())
                    .isTeacher(user.getRoles().stream()
                            .anyMatch(role -> role.getName().equals("ROLE_TEACHER")))
                    .build();

            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            LoginResponse response = LoginResponse.builder()
                    .success(false)
                    .message("Invalid username or password")
                    .build();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @Operation(
            summary = "Student registration",
            description = "Register a new student account"
    )
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

    @Operation(
            summary = "Teacher registration",
            description = "Register a new teacher account"
    )
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

    @Operation(
            summary = "Logout",
            description = "Logout the current user and invalidate the session"
    )
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Logout successful");

        return ResponseEntity.ok(response);
    }
    @GetMapping("/check")
    @Operation(summary = "Check authentication status", description = "Returns details about the currently authenticated user")
    @SecurityRequirement(name = "basicAuth")
    public ResponseEntity<Map<String, Object>> checkAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(authentication.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());

        // Add user roles
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        boolean isTeacher = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_TEACHER"));
        boolean isStudent = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_STUDENT"));

        response.put("isAdmin", isAdmin);
        response.put("isTeacher", isTeacher);
        response.put("isStudent", isStudent);

        return ResponseEntity.ok(response);
    }
}
