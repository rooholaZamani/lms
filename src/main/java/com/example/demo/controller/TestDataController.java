package com.example.demo.controller;

import com.example.demo.service.TestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test-data")
@CrossOrigin(origins = "*")
public class TestDataController {

    @Autowired
    private TestDataService testDataService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateTestData() {
        try {
            String result = testDataService.generateComprehensiveTestData();

            if (result.startsWith("SUCCESS")) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", result
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to generate test data: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTestDataSummary() {
        try {
            Map<String, Object> summary = testDataService.getTestDataSummary();
            summary.put("status", "success");
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get test data summary: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/generate-activity-logs")
    public ResponseEntity<Map<String, String>> generateActivityLogs() {
        try {
            String result = testDataService.generateActivityLogsForExistingUsers();

            if (result.startsWith("SUCCESS")) {
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", result
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", result
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to generate activity logs: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "message", "Test data generation service is ready"
        ));
    }
}