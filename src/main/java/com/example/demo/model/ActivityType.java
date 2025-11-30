package com.example.demo.model;

/**
 * Enum representing different types of user activities in the LMS.
 * Each activity type has a Persian label for UI display.
 */
public enum ActivityType {
    CONTENT_VIEW("مشاهده محتوا"),
    CONTENT_COMPLETE("تکمیل محتوا"),
    EXAM_START("شروع آزمون"),
    EXAM_SUBMIT("ارسال آزمون"),
    ASSIGNMENT_VIEW("مشاهده تکلیف"),
    ASSIGNMENT_SUBMIT("ارسال تکلیف"),
    CHAT_MESSAGE("ارسال پیام"),
    LOGIN("ورود"),
    LOGOUT("خروج"),
    COURSE_ENROLL("ثبت‌نام در دوره"),
    LESSON_VIEW("مشاهده درس"),
    FILE_DOWNLOAD("دانلود فایل"),
    PROGRESS_UPDATE("به‌روزرسانی پیشرفت");

    private final String persianLabel;

    ActivityType(String persianLabel) {
        this.persianLabel = persianLabel;
    }

    public String getPersianLabel() {
        return persianLabel;
    }

    public String getEnglishLabel() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }

    /**
     * Get ActivityType from string value (case-insensitive).
     *
     * @param value The string representation of activity type
     * @return The matching ActivityType, or null if not found
     */
    public static ActivityType fromString(String value) {
        if (value == null) {
            return null;
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if this activity type is related to content consumption.
     *
     * @return true if this is a content-related activity
     */
    public boolean isContentActivity() {
        return this == CONTENT_VIEW || this == CONTENT_COMPLETE || this == LESSON_VIEW;
    }

    /**
     * Check if this activity type is related to assessments.
     *
     * @return true if this is an assessment-related activity
     */
    public boolean isAssessmentActivity() {
        return this == EXAM_START || this == EXAM_SUBMIT ||
               this == ASSIGNMENT_VIEW || this == ASSIGNMENT_SUBMIT;
    }

    /**
     * Check if this activity type is related to communication.
     *
     * @return true if this is a communication-related activity
     */
    public boolean isCommunicationActivity() {
        return this == CHAT_MESSAGE;
    }
}
