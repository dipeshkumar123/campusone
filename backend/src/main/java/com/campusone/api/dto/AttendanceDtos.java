package com.campusone.api.dto;

import com.campusone.domain.AttendanceRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class AttendanceDtos {
    private AttendanceDtos() {
    }

    public record MyClassesView(
            String date,
            String dateLabel,
            List<ClassCard> classes,
            List<TeachingSubject> subjects,
            int attendanceThreshold
    ) {
    }

    public record ClassCard(
            Long subjectId,
            String subjectCode,
            String subjectName,
            String programmeCode,
            int studyYear,
            int period,
            String room,
            boolean submitted,
            String submittedAt,
            Integer presentCount,
            Integer studentCount,
            Double attendancePercent
    ) {
    }

    public record TeachingSubject(
            Long id,
            String code,
            String name,
            int studyYear,
            String programmeCode
    ) {
    }

    public record RosterView(
            Long subjectId,
            String subjectCode,
            String subjectName,
            String programmeCode,
            String programmeName,
            int studyYear,
            String date,
            int period,
            int attendanceThreshold,
            SessionInfo session,
            List<RosterStudent> students,
            Counts counts,
            boolean canEdit
    ) {
    }

    public record SessionInfo(Long id, String submittedAt, String recordedBy) {
    }

    public record RosterStudent(
            Long id,
            String rollNo,
            String name,
            Double attendancePercent,
            boolean belowThreshold,
            AttendanceRecord.Status status
    ) {
    }

    public record Counts(int present, int absent, int late, int unmarked) {
    }

    public record SyncRequest(
            @NotNull(message = "Subject is required.")
            Long subjectId,
            @NotNull(message = "Class date is required.")
            LocalDate date,
            @Min(value = 1, message = "Period must be between 1 and 8.")
            @Max(value = 8, message = "Period must be between 1 and 8.")
            int period,
            @NotBlank(message = "Sync key is required.")
            @Size(min = 8, max = 80, message = "Sync key is invalid.")
            String clientId,
            @NotEmpty(message = "Attendance records are required.")
            @Size(max = 200, message = "This class has too many students.")
            List<@Valid SyncRecord> records
    ) {
    }

    public record SyncRecord(
            @NotNull(message = "Student is required.")
            Long studentId,
            @NotNull(message = "Attendance status is required.")
            AttendanceRecord.Status status
    ) {
    }

    public record SyncResponse(
            Long sessionId,
            String date,
            int period,
            String submittedAt,
            boolean duplicate,
            int presentCount,
            int absentCount,
            int total,
            String message
    ) {
    }
}
