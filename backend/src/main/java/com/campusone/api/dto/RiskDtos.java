package com.campusone.api.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RiskDtos {
    private RiskDtos() {
    }

    public record Summary(
            int openFlags,
            int high,
            int medium,
            int tasksOpen,
            String computedAt,
            String scopeNote
    ) {
    }

    public record MentorTaskView(
            Long id,
            Long assigneeId,
            String assigneeName,
            String note,
            String dueDate,
            String status,
            String createdBy,
            String createdAt
    ) {
    }

    public record RiskItem(
            Long flagId,
            Long studentId,
            String studentName,
            String rollNo,
            String programmeCode,
            String programmeName,
            int studyYear,
            int score,
            String band,
            String status,
            List<String> reasons,
            BigDecimal attendancePercent,
            BigDecimal marksPercent,
            BigDecimal trendDelta,
            BigDecimal feeDue,
            MentorTaskView mentorTask,
            String computedAt
    ) {
    }

    public record PageView(Summary summary, List<RiskItem> flags) {
    }

    public record RecomputeResult(int flagged, int high, int medium, int resolved, int tasksCreated, String message) {
    }

    public record CreateTaskRequest(
            @NotNull(message = "Choose a mentor.")
            Long assigneeId,

            @NotBlank(message = "Add a short note for the mentor.")
            @Size(min = 5, max = 500, message = "Note must be between 5 and 500 characters.")
            String note,

            @NotNull(message = "Choose a due date.")
            @FutureOrPresent(message = "Due date cannot be in the past.")
            LocalDate dueDate
    ) {
    }

    public record TaskResponse(MentorTaskView task, String message) {
    }
}
