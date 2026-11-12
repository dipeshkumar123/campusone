package com.campusone.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record View(
            UserView user,
            String today,
            String dayLabel,
            String academicYear,
            String scopeNote,
            Metrics metrics,
            List<TodayClass> classes,
            List<RiskPreview> risks,
            List<DuePreview> dues,
            List<WeekPoint> attendanceTrend,
            Automation automation,
            List<SubjectPreview> pendingModeration
    ) {
    }

    public record Metrics(
            int activeStudents,
            int classesScheduledToday,
            int classesSubmittedToday,
            Double attendanceThisWeek,
            int belowThreshold,
            BigDecimal feeOutstanding,
            int defaulters,
            int openRiskFlags,
            int mentorTasksOpen,
            int pendingModeration
    ) {
    }

    public record TodayClass(
            Long subjectId,
            String subjectCode,
            String subjectName,
            String programmeCode,
            int studyYear,
            int period,
            String room,
            String facultyName,
            boolean submitted,
            Integer presentCount,
            Integer studentCount,
            Double attendancePercent
    ) {
    }

    public record RiskPreview(
            Long flagId,
            Long studentId,
            String studentName,
            String rollNo,
            String programmeCode,
            int score,
            String band,
            String topReason
    ) {
    }

    public record DuePreview(
            Long studentId,
            String studentName,
            String rollNo,
            String programmeCode,
            BigDecimal dueAmount,
            String dueDate,
            String status
    ) {
    }

    public record WeekPoint(String label, Double attendancePercent, int classes) {
    }

    public record Automation(
            int reminderCandidates,
            String lastReminderAt,
            int atRiskAlerts,
            String alertsRefreshedAt
    ) {
    }

    public record SubjectPreview(
            Long subjectId,
            String code,
            String name,
            String programmeCode,
            String moderationStatus,
            int enteredScores,
            int expectedScores
    ) {
    }
}
