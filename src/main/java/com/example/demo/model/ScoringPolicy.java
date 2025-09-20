package com.example.demo.model;

/**
 * Defines how partial scoring should be calculated for questions that support it
 * (MATCHING, CATEGORIZATION)
 */
public enum ScoringPolicy {
    /**
     * Standard rounding - 0.5 and above rounds up
     * Example: 1.5 points becomes 2, 1.4 points becomes 1
     */
    ROUND_STANDARD,

    /**
     * Always round up (ceiling) - any partial credit rounds up to next integer
     * Example: 1.1 points becomes 2, 1.0 points stays 1
     */
    ROUND_UP,

    /**
     * Always round down (floor) - any partial credit rounds down
     * Example: 1.9 points becomes 1, 2.0 points stays 2
     */
    ROUND_DOWN,

    /**
     * Use exact decimal scoring - don't round to integers
     * Example: 1.67 points stays 1.67 (requires decimal point support)
     */
    EXACT_DECIMAL
}