package com.campusone.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class StudentDtos {
    private StudentDtos() {
    }

    public record Row(
            Long id,
            String rollNo,
            String name,
            String programmeCode,
            String programmeName,
            int studyYear,
            String section,
            String category,
            Double attendancePercent,
            Double internalMarks40,
            BigDecimal feeDue,
            String riskBand,
            String riskStatus,
            String consentStatus
    ) {
    }

    public record ListView(List<Row> rows, int total) {
    }

    public record SubjectAttendance(Long subjectId, String code, String name, int present, int held,
                                    Double percent, boolean belowThreshold) {
    }

    public record ComponentScore(Long componentId, String name, int maxScore, BigDecimal score) {
    }

    public record SubjectMarks(Long subjectId, String code, String name, String moderationStatus,
                               List<ComponentScore> components, BigDecimal total, int maxTotal) {
    }

    public record MentorTaskSummary(Long id, String assigneeName, String dueDate, String status, String note) {
    }

    public record RiskSnapshot(Long flagId, int score, String band, String status, List<String> reasons,
                               BigDecimal attendancePercent, BigDecimal marksPercent, BigDecimal trendDelta,
                               MentorTaskSummary mentorTask) {
    }

    public record Profile(
            Long id,
            String rollNo,
            String name,
            String email,
            String programmeCode,
            String programmeName,
            String department,
            int studyYear,
            String section,
            String batch,
            String category,
            String gender,
            String consentStatus,
            String guardianName,
            String guardianPhone,
            String admittedOn,
            String status,
            Double attendancePercent,
            int presentCount,
            int heldCount,
            Double internalMarks40,
            Double internalMarksPercent,
            List<SubjectAttendance> attendanceBySubject,
            List<SubjectMarks> marksBySubject,
            RiskSnapshot risk,
            FeeDtos.FeeDetail fee,
            String academicYear,
            int attendanceThreshold
    ) {
    }
}
