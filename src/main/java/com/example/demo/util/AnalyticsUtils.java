package com.example.demo.util;

import com.example.demo.model.ActivityLog;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for analytics-related helper methods
 * Extracted from AnalyticsService to reduce code duplication
 */
public class AnalyticsUtils {

    private static final ZoneId IRAN_TIMEZONE = ZoneId.of("Asia/Tehran");

    // ==================== Time Utilities ====================

    /**
     * Unified method to get Iran Standard Time with optional offset
     * @param amount The amount to add/subtract (0 for current time, negative for past)
     * @param unit The time unit (DAYS, MONTHS, HOURS, etc.)
     * @return LocalDateTime in Iran timezone
     */
    public static LocalDateTime getIranTime(long amount, ChronoUnit unit) {
        ZonedDateTime iranTime = ZonedDateTime.now(IRAN_TIMEZONE);
        return amount == 0 ? iranTime.toLocalDateTime() :
               iranTime.plus(amount, unit).toLocalDateTime();
    }

    /**
     * Get current time in Iran timezone
     */
    public static LocalDateTime getNowInIranTime() {
        return getIranTime(0, ChronoUnit.DAYS);
    }

    /**
     * Get Iran time for specific days ago
     */
    public static LocalDateTime getIranTimeMinusDays(int days) {
        return getIranTime(-days, ChronoUnit.DAYS);
    }

    /**
     * Get Iran time for specific months ago
     */
    public static LocalDateTime getIranTimeMinusMonths(int months) {
        return getIranTime(-months, ChronoUnit.MONTHS);
    }

    /**
     * Calculate start date based on period filter
     */
    public static LocalDateTime calculateStartDate(LocalDateTime endDate, String period) {
        LocalDateTime now = getNowInIranTime();

        switch (period.toLowerCase()) {
            case "day":
            case "daily":
                return now.minusDays(1);
            case "week":
            case "weekly":
                return now.minusDays(7);
            case "month":
            case "monthly":
                return now.minusMonths(1);
            case "3months":
            case "quarter":
                return now.minusMonths(3);
            case "6months":
            case "semester":
                return now.minusMonths(6);
            case "year":
            case "yearly":
                return now.minusYears(1);
            case "all":
                return LocalDateTime.of(2020, 1, 1, 0, 0); // Very old date
            default:
                return now.minusMonths(1); // Default to 1 month
        }
    }

    // ==================== Date Formatters ====================

    /**
     * Get Persian day name from DayOfWeek
     */
    public static String getDayName(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case SATURDAY: return "شنبه";
            case SUNDAY: return "یکشنبه";
            case MONDAY: return "دوشنبه";
            case TUESDAY: return "سه‌شنبه";
            case WEDNESDAY: return "چهارشنبه";
            case THURSDAY: return "پنج‌شنبه";
            case FRIDAY: return "جمعه";
            default: return "";
        }
    }

    /**
     * Get Persian day name from integer (0-6 where 0=Saturday)
     */
    public static String getDayName(int dayOfWeek) {
        String[] days = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"};
        return days[dayOfWeek % 7];
    }

    /**
     * Get Persian month name
     */
    public static String getMonthName(int month) {
        String[] persianMonths = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        };
        return month >= 1 && month <= 12 ? persianMonths[month - 1] : "";
    }

    // ==================== Activity Type Labels ====================

    private static final Map<String, String> ACTIVITY_TYPE_LABELS = new HashMap<String, String>() {{
        put("LOGIN", "ورود به سیستم");
        put("LOGOUT", "خروج از سیستم");
        put("CONTENT_VIEW", "مشاهده محتوا");
        put("CONTENT_COMPLETION", "تکمیل محتوا");
        put("LESSON_COMPLETION", "تکمیل درس");
        put("EXAM_SUBMISSION", "شرکت در آزمون");
        put("ASSIGNMENT_SUBMISSION", "ارسال تکلیف");
        put("COURSE_ENROLLMENT", "ثبت‌نام در دوره");
        put("CHAT_MESSAGE", "ارسال پیام");
        put("FILE_DOWNLOAD", "دانلود فایل");
        put("FORUM_POST", "ارسال پست");
        put("FORUM_REPLY", "پاسخ به پست");
    }};

    /**
     * Get Persian label for activity type
     */
    public static String getActivityTypeLabel(String activityType) {
        return ACTIVITY_TYPE_LABELS.getOrDefault(activityType, "فعالیت نامشخص");
    }

    // ==================== Content Type Labels ====================

    private static final Map<String, String> CONTENT_TYPE_LABELS = new HashMap<String, String>() {{
        put("VIDEO", "ویدیو");
        put("PDF", "PDF");
        put("TEXT", "متن");
        put("AUDIO", "صوت");
        put("IMAGE", "تصویر");
        put("DOCUMENT", "سند");
        put("QUIZ", "آزمون");
        put("ASSIGNMENT", "تکلیف");
    }};

    /**
     * Get Persian label for content type
     */
    public static String getContentTypeLabel(String contentType) {
        return CONTENT_TYPE_LABELS.getOrDefault(contentType, "محتوای نامشخص");
    }

    // ==================== Generic Filters ====================

    /**
     * Generic method to filter items by course ID
     */
    public static <T> List<T> filterByCourse(
            List<T> items,
            Long courseId,
            Function<T, Long> courseExtractor) {
        return items.stream()
                .filter(item -> courseId.equals(courseExtractor.apply(item)))
                .collect(Collectors.toList());
    }

    /**
     * Count activities by specific types
     */
    public static long countActivitiesByType(List<ActivityLog> activities, String... types) {
        List<String> typeList = List.of(types);
        return activities.stream()
                .filter(a -> typeList.contains(a.getActivityType()))
                .count();
    }

    // ==================== Statistical Calculations ====================

    /**
     * Calculate percentile of a value within an array of values
     */
    public static double calculatePercentile(double value, double[] values) {
        if (values.length == 0) return 0;

        long count = 0;
        for (double v : values) {
            if (v < value) count++;
        }

        return (double) count / values.length * 100;
    }

    /**
     * Calculate average from list of numbers
     */
    public static double calculateAverage(List<? extends Number> numbers) {
        if (numbers == null || numbers.isEmpty()) return 0.0;

        return numbers.stream()
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculate percentage safely (avoiding division by zero)
     */
    public static double calculatePercentage(long part, long total) {
        if (total == 0) return 0.0;
        return (double) part / total * 100.0;
    }

    /**
     * Round to 2 decimal places
     */
    public static double roundTo2Decimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ==================== Difficulty & Label Helpers ====================

    /**
     * Get difficulty label based on average time (in seconds)
     */
    public static String getDifficultyLabel(double avgTimeSeconds) {
        if (avgTimeSeconds < 120) return "آسان";      // < 2 minutes
        if (avgTimeSeconds < 300) return "متوسط";     // < 5 minutes
        if (avgTimeSeconds < 600) return "سخت";       // < 10 minutes
        return "خیلی سخت";
    }

    /**
     * Calculate efficiency score based on activity type and time
     */
    public static double calculateEfficiencyScore(String activityType, double avgTimeSeconds) {
        switch (activityType) {
            case "CONTENT_VIEW":
                return avgTimeSeconds < 300 ? 95 : (avgTimeSeconds < 600 ? 85 : 70);
            case "ASSIGNMENT_SUBMISSION":
                return avgTimeSeconds < 1800 ? 90 : (avgTimeSeconds < 3600 ? 80 : 65);
            default:
                return 80.0;
        }
    }

    // ==================== Trend Calculations ====================

    /**
     * Calculate trend percentage by comparing two periods
     */
    public static int calculateTrend(List<ActivityLog> activities, int days) {
        if (activities.size() < days) return 0;

        int halfDays = days / 2;
        LocalDateTime midPoint = LocalDateTime.now().minusDays(halfDays);

        long recentCount = activities.stream()
                .filter(a -> a.getTimestamp().isAfter(midPoint))
                .count();

        long earlierCount = activities.stream()
                .filter(a -> a.getTimestamp().isBefore(midPoint))
                .count();

        if (earlierCount == 0) return 0;

        return (int) (((double) recentCount - earlierCount) / earlierCount * 100);
    }

    /**
     * Convert seconds to hours with 2 decimal places
     */
    public static double secondsToHours(long seconds) {
        return roundTo2Decimals(seconds / 3600.0);
    }

    /**
     * Convert seconds to formatted string (e.g., "2h 30m")
     */
    public static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm", minutes);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
