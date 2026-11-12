package com.campusone.api.dto;

import com.campusone.domain.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class FeeDtos {
    private FeeDtos() {
    }

    public record Summary(
            BigDecimal totalBilled,
            BigDecimal totalCollected,
            BigDecimal totalOutstanding,
            int defaulters,
            int reminderCandidates,
            String lastReminderAt,
            int collectionRatePercent,
            String academicYear
    ) {
    }

    public record FeeRow(
            Long accountId,
            Long studentId,
            String rollNo,
            String studentName,
            String programmeCode,
            String programmeName,
            int studyYear,
            String category,
            BigDecimal grossAmount,
            BigDecimal scholarshipAmount,
            BigDecimal paidAmount,
            BigDecimal dueAmount,
            String dueDate,
            String status,
            int daysOverdue,
            String lastReminderAt
    ) {
    }

    public record RecentPayment(
            Long paymentId,
            String receiptNo,
            String studentName,
            String rollNo,
            BigDecimal amount,
            String method,
            String paidAt,
            Long studentId
    ) {
    }

    public record ListView(
            Summary summary,
            List<FeeRow> rows,
            List<RecentPayment> recentPayments,
            int rowCount
    ) {
    }

    public record PayRequest(
            @NotNull(message = "Amount is required.")
            @DecimalMin(value = "1.00", message = "Amount must be at least ₹1.")
            BigDecimal amount,

            @NotNull(message = "Choose a payment method.")
            Payment.Method method,

            @NotBlank(message = "Payment reference is required.")
            @Size(min = 8, max = 80, message = "Payment reference is invalid.")
            String idempotencyKey
    ) {
    }

    public record PaymentView(
            Long id,
            String receiptNo,
            String reference,
            BigDecimal amount,
            String method,
            String status,
            String paidAt,
            String studentName,
            String rollNo,
            String programmeCode,
            String academicYear,
            String recordedBy,
            boolean duplicate
    ) {
    }

    public record FeeDetail(FeeRow account, List<PaymentView> payments) {
    }

    public record ReminderResult(
            int count,
            String queuedAt,
            String messageEnglish,
            String messageOdia
    ) {
    }
}
