package com.example.demo.model;

/**
 * Enum representing percentage-based grade categories for educational assessment.
 * Provides standardized grading categories with associated percentage ranges and display colors.
 */
public enum GradeCategory {
    POOR(0, 50, "#dc3545", "ضعیف"),           // Red - Poor performance
    AVERAGE(50, 70, "#ffc107", "متوسط"),      // Yellow - Average performance
    GOOD(70, 90, "#17a2b8", "خوب"),          // Blue - Good performance
    EXCELLENT(90, 100, "#28a745", "عالی");    // Green - Excellent performance

    private final int minPercentage;
    private final int maxPercentage;
    private final String color;
    private final String persianLabel;

    GradeCategory(int minPercentage, int maxPercentage, String color, String persianLabel) {
        this.minPercentage = minPercentage;
        this.maxPercentage = maxPercentage;
        this.color = color;
        this.persianLabel = persianLabel;
    }

    /**
     * Determines the grade category based on percentage score.
     *
     * @param percentage The percentage score (0-100)
     * @return The appropriate GradeCategory
     */
    public static GradeCategory fromPercentage(double percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }

        for (GradeCategory category : values()) {
            if (percentage >= category.minPercentage && percentage < category.maxPercentage) {
                return category;
            }
        }

        // Handle edge case where percentage is exactly 100
        if (percentage == 100) {
            return EXCELLENT;
        }

        return POOR; // Fallback
    }

    /**
     * Calculates percentage from raw score and maximum possible score.
     *
     * @param score The achieved score
     * @param maxScore The maximum possible score
     * @return The percentage score
     */
    public static double calculatePercentage(double score, double maxScore) {
        if (maxScore <= 0) {
            throw new IllegalArgumentException("Maximum score must be greater than 0");
        }
        return Math.min(100.0, Math.max(0.0, (score / maxScore) * 100.0));
    }

    /**
     * Gets the grade category from raw score and maximum score.
     *
     * @param score The achieved score
     * @param maxScore The maximum possible score
     * @return The appropriate GradeCategory
     */
    public static GradeCategory fromScore(double score, double maxScore) {
        double percentage = calculatePercentage(score, maxScore);
        return fromPercentage(percentage);
    }

    /**
     * Checks if this category represents a passing grade (Average or better).
     *
     * @return true if the grade is passing (>= 50%)
     */
    public boolean isPassing() {
        return this != POOR;
    }

    /**
     * Gets the display range as a string (e.g., "70-90%").
     *
     * @return The percentage range string
     */
    public String getRange() {
        if (maxPercentage == 100) {
            return minPercentage + "-" + maxPercentage + "%";
        }
        return minPercentage + "-" + (maxPercentage - 1) + "%";
    }

    // Getters
    public int getMinPercentage() {
        return minPercentage;
    }

    public int getMaxPercentage() {
        return maxPercentage;
    }

    public String getColor() {
        return color;
    }

    public String getPersianLabel() {
        return persianLabel;
    }

    public String getEnglishLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}