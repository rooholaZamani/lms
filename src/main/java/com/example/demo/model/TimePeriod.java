package com.example.demo.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Enum representing different time period filters for analytics.
 * Provides standardized time ranges with Persian labels.
 */
public enum TimePeriod {
    DAY(1, ChronoUnit.DAYS, "روز", "امروز"),
    WEEK(7, ChronoUnit.DAYS, "هفته", "هفته گذشته"),
    MONTH(30, ChronoUnit.DAYS, "ماه", "ماه گذشته"),
    QUARTER(90, ChronoUnit.DAYS, "سه ماهه", "سه ماه گذشته"),
    YEAR(365, ChronoUnit.DAYS, "سال", "سال گذشته"),
    ALL_TIME(Integer.MAX_VALUE, ChronoUnit.DAYS, "همه زمان‌ها", "از ابتدا تا کنون");

    private final long amount;
    private final ChronoUnit unit;
    private final String persianLabel;
    private final String persianDescription;

    TimePeriod(long amount, ChronoUnit unit, String persianLabel, String persianDescription) {
        this.amount = amount;
        this.unit = unit;
        this.persianLabel = persianLabel;
        this.persianDescription = persianDescription;
    }

    /**
     * Calculate start date from current time based on this period.
     *
     * @param endDate The end date (typically now)
     * @return The calculated start date
     */
    public LocalDateTime getStartDate(LocalDateTime endDate) {
        if (this == ALL_TIME) {
            return LocalDateTime.of(2020, 1, 1, 0, 0); // Default start date
        }
        return endDate.minus(amount, unit);
    }

    /**
     * Get TimePeriod from string representation (e.g., "7", "30", "365").
     * This is for backward compatibility with existing string-based filters.
     *
     * @param value The string value representing days
     * @return The matching TimePeriod, or MONTH as default
     */
    public static TimePeriod fromDays(String value) {
        if (value == null) {
            return MONTH;
        }

        try {
            int days = Integer.parseInt(value);
            return fromDays(days);
        } catch (NumberFormatException e) {
            return MONTH;
        }
    }

    /**
     * Get TimePeriod from number of days.
     *
     * @param days The number of days
     * @return The matching TimePeriod
     */
    public static TimePeriod fromDays(int days) {
        if (days <= 1) return DAY;
        if (days <= 7) return WEEK;
        if (days <= 30) return MONTH;
        if (days <= 90) return QUARTER;
        if (days <= 365) return YEAR;
        return ALL_TIME;
    }

    /**
     * Get TimePeriod from enum name (case-insensitive).
     *
     * @param name The enum name
     * @return The matching TimePeriod, or MONTH as default
     */
    public static TimePeriod fromName(String name) {
        if (name == null) {
            return MONTH;
        }

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MONTH;
        }
    }

    // Getters
    public long getAmount() {
        return amount;
    }

    public ChronoUnit getUnit() {
        return unit;
    }

    public String getPersianLabel() {
        return persianLabel;
    }

    public String getPersianDescription() {
        return persianDescription;
    }

    public String getEnglishLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /**
     * Get the number of days represented by this period.
     *
     * @return Number of days
     */
    public int getDays() {
        if (this == ALL_TIME) {
            return Integer.MAX_VALUE;
        }
        return (int) amount;
    }
}
