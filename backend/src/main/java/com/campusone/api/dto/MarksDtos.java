package com.campusone.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class MarksDtos {
    private MarksDtos() {
    }

    public record SubjectInfo(Long id, String code, String name, String programmeCode, String programmeName,
                              int studyYear, int credits) {
    }

    public record ComponentView(Long id, String name, int maxScore, int sortOrder) {
    }

    public record StateView(String status, String lockedBy, String lockedAt, String note, String unlockedBy,
                            String unlockedAt, String unlockReason) {
    }

    public record Row(Long studentId, String rollNo, String name, Double attendancePercent,
                      Map<Long, BigDecimal> scores, BigDecimal total) {
    }

    public record Stats(int students, int enteredScores, int expectedScores, double averageTotal,
                        double completionPercent, int belowThresholdStudents) {
    }

    public record AuditView(Long id, String actor, String action, String detail, String at) {
    }

    public record Sheet(SubjectInfo subject, List<ComponentView> components, StateView state, List<Row> rows,
                        Stats stats, boolean canEdit, boolean canModerate, List<AuditView> audit,
                        int attendanceThreshold) {
    }

    public record SubjectCard(Long id, String code, String name, String programmeCode, String programmeName,
                              int studyYear, String moderationStatus, int enrolled, int enteredScores,
                              int expectedScores, int belowThresholdStudents) {
    }

    public record SaveEntry(
            @NotNull(message = "Student is required.")
            Long studentId,
            @NotNull(message = "Component is required.")
            Long componentId,
            @NotNull(message = "Score is required.")
            @DecimalMin(value = "0.0", message = "Score cannot be negative.")
            BigDecimal score
    ) {
    }

    public record SaveRequest(
            @NotEmpty(message = "Add at least one mark.")
            @Size(max = 400, message = "Too many marks in one save.")
            List<@Valid SaveEntry> entries
    ) {
    }

    public record LockRequest(@Size(max = 500, message = "Note must be 500 characters or fewer.") String note) {
    }

    public record UnlockRequest(
            @NotBlank(message = "A reason is required to unlock marks.")
            @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters.")
            String reason
    ) {
    }
}
