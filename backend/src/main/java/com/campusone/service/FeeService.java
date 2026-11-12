package com.campusone.service;

import com.campusone.api.dto.FeeDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.Payment;
import com.campusone.domain.Student;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.PaymentRepository;
import com.campusone.repo.ProgrammeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FeeService {
    private final AccessService accessService;
    private final FeeAccountRepository feeAccountRepository;
    private final PaymentRepository paymentRepository;
    private final ProgrammeRepository programmeRepository;
    private final AuditService auditService;
    private final AcademicCalendar calendar;
    private final Clock clock;

    public FeeService(AccessService accessService, FeeAccountRepository feeAccountRepository,
                      PaymentRepository paymentRepository, ProgrammeRepository programmeRepository,
                      AuditService auditService, AcademicCalendar calendar, Clock clock) {
        this.accessService = accessService;
        this.feeAccountRepository = feeAccountRepository;
        this.paymentRepository = paymentRepository;
        this.programmeRepository = programmeRepository;
        this.auditService = auditService;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FeeDtos.ListView list(String query, Long programmeId, String status) {
        accessService.user();
        if (!accessService.user().isAdmin()) {
            throw ApiException.forbidden("Fee records are limited to the office.");
        }
        List<FeeAccount> accounts = feeAccountRepository.findAllByOrderByDueAmountDesc();
        List<FeeDtos.FeeRow> allRows = accounts.stream().map(this::row).toList();

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String filterStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<FeeDtos.FeeRow> rows = allRows.stream()
                .filter(row -> q.isBlank() || row.studentName().toLowerCase(Locale.ROOT).contains(q)
                        || row.rollNo().toLowerCase(Locale.ROOT).contains(q))
                .filter(row -> programmeId == null
                        || row.programmeCode().equals(programmeRepository.findById(programmeId)
                        .map(p -> p.getCode()).orElse("")))
                .filter(row -> filterStatus.isBlank() || row.status().equals(filterStatus))
                .toList();

        BigDecimal billed = sum(allRows, FeeDtos.FeeRow::grossAmount).subtract(sum(allRows, FeeDtos.FeeRow::scholarshipAmount));
        BigDecimal collected = sum(allRows, FeeDtos.FeeRow::paidAmount);
        BigDecimal outstanding = sum(allRows, FeeDtos.FeeRow::dueAmount);
        int defaulters = (int) allRows.stream().filter(r -> r.dueAmount().signum() > 0).count();
        int candidates = (int) accounts.stream().filter(this::reminderDue).count();
        String lastReminder = accounts.stream()
                .map(FeeAccount::getLastReminderAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).map(Instant::toString).orElse(null);
        int collectionRate = billed.signum() == 0 ? 0
                : collected.multiply(BigDecimal.valueOf(100)).divide(billed, 0, RoundingMode.HALF_UP).intValue();

        List<FeeDtos.RecentPayment> recent = paymentRepository.findTop8ByOrderByPaidAtDesc().stream()
                .map(p -> new FeeDtos.RecentPayment(p.getId(), p.getReceiptNo(), p.getFeeAccount().getStudent().getName(),
                        p.getFeeAccount().getStudent().getRollNo(), p.getAmount(), p.getMethod().name(),
                        p.getPaidAt().toString(), p.getFeeAccount().getStudent().getId()))
                .toList();

        return new FeeDtos.ListView(new FeeDtos.Summary(billed, collected, outstanding, defaulters, candidates,
                lastReminder, collectionRate, calendar.academicYear()), rows, recent, rows.size());
    }

    @Transactional(readOnly = true)
    public FeeDtos.FeeDetail detail(Long studentId) {
        accessService.requireAdmin();
        FeeAccount account = feeAccountRepository.findByStudentId(studentId)
                .orElseThrow(() -> ApiException.notFound("No fee account exists for this student."));
        List<FeeDtos.PaymentView> payments = paymentRepository.findByFeeAccountIdOrderByPaidAtDesc(account.getId())
                .stream().map(p -> paymentView(p, false)).toList();
        return new FeeDtos.FeeDetail(row(account), payments);
    }

    @Transactional
    public FeeDtos.PaymentView pay(Long studentId, FeeDtos.PayRequest request) {
        accessService.requireAdmin();
        FeeAccount account = feeAccountRepository.findByStudentId(studentId)
                .orElseThrow(() -> ApiException.notFound("No fee account exists for this student."));
        Payment existing = paymentRepository.findByReference(request.idempotencyKey().trim()).orElse(null);
        if (existing != null) {
            if (!existing.getFeeAccount().getId().equals(account.getId())) {
                throw ApiException.conflict("This payment reference belongs to a different account.");
            }
            return paymentView(existing, true);
        }
        if (request.amount().compareTo(account.getDueAmount()) > 0) {
            throw ApiException.badRequest("Payment cannot be more than the outstanding amount of ₹"
                    + account.getDueAmount().setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
        if (account.getDueAmount().signum() <= 0) {
            throw ApiException.conflict("This account is already cleared.");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("Payment amount must be greater than zero.");
        }
        String receiptNo = nextReceiptNo();
        Payment payment = new Payment(account, amount, request.method(), receiptNo, request.idempotencyKey().trim(),
                accessService.user().name());
        payment.setPaidAt(clock.instant());
        paymentRepository.save(payment);
        account.setPaidAmount(account.getPaidAmount().add(amount));
        account.setDueAmount(account.getDueAmount().subtract(amount).max(BigDecimal.ZERO));
        feeAccountRepository.save(account);
        auditService.log("FEE_PAYMENT", "FEE_ACCOUNT", account.getId().toString(),
                "₹" + amount.toPlainString() + " received · receipt " + receiptNo + " · test mode "
                        + request.method());
        return paymentView(payment, false);
    }

    @Transactional
    public FeeDtos.ReminderResult sendReminders() {
        accessService.requireAdmin();
        List<FeeAccount> candidates = feeAccountRepository.findAllByOrderByDueAmountDesc().stream()
                .filter(this::reminderDue).toList();
        Instant now = clock.instant();
        candidates.forEach(account -> {
            account.setLastReminderAt(now);
            feeAccountRepository.save(account);
        });
        auditService.log("FEE_REMINDERS_QUEUED", "FEE_ACCOUNT", "batch",
                candidates.size() + " fee reminders queued in mock SMS mode · Odia parent copy");
        String english = "Dear parent, the fee balance for your child is pending. Please pay via the CampusOne "
                + "payment link or contact the accounts office.";
        String odia = "ପ୍ରିୟ ଅଭିଭାବକ, ଆପଣଙ୍କ ସନ୍ତାନଙ୍କ ଫି ବାକି ଅଛି। ଦୟାକରି CampusOne ପେମେଣ୍ଟ ଲିଙ୍କରେ ପରିଶୋଧ କରନ୍ତୁ।";
        return new FeeDtos.ReminderResult(candidates.size(), now.toString(), english, odia);
    }

    @Transactional(readOnly = true)
    public String tallyCsv() {
        accessService.requireAdmin();
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("VOUCHER_DATE,VOUCHER_NO,STUDENT_ROLL,STUDENT_NAME,PROGRAMME,RECEIPT_NO,MODE,LEDGER,AMOUNT\n");
        List<Payment> payments = paymentRepository.findAll().stream()
                .sorted(Comparator.comparing(Payment::getPaidAt)).toList();
        for (Payment payment : payments) {
            Student student = payment.getFeeAccount().getStudent();
            csv.append(payment.getPaidAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()).append(',')
                    .append(csvText(payment.getReference())).append(',')
                    .append(csvText(student.getRollNo())).append(',')
                    .append(csvText(student.getName())).append(',')
                    .append(csvText(student.getProgramme().getCode())).append(',')
                    .append(csvText(payment.getReceiptNo())).append(',')
                    .append(payment.getMethod()).append(',')
                    .append("Tuition Fees").append(',')
                    .append(payment.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()).append('\n');
        }
        return csv.toString();
    }

    private boolean reminderDue(FeeAccount account) {
        if (account.getDueAmount().signum() <= 0) {
            return false;
        }
        return account.getLastReminderAt() == null
                || account.getLastReminderAt().isBefore(calendar.today().atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant());
    }

    private FeeDtos.FeeRow row(FeeAccount account) {
        Student student = account.getStudent();
        LocalDate dueDate = account.getDueDate();
        LocalDate today = calendar.today();
        int daysOverdue = dueDate.isBefore(today) && account.getDueAmount().signum() > 0
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, today) : 0;
        String status = account.getDueAmount().signum() <= 0 ? "CLEARED" : (daysOverdue > 0 ? "OVERDUE" : "DUE");
        return new FeeDtos.FeeRow(account.getId(), student.getId(), student.getRollNo(), student.getName(),
                student.getProgramme().getCode(), student.getProgramme().getName(), student.getStudyYear(),
                student.getCategory().name(), account.getGrossAmount(), account.getScholarshipAmount(),
                account.getPaidAmount(), account.getDueAmount(), dueDate.toString(), status, daysOverdue,
                account.getLastReminderAt() == null ? null : account.getLastReminderAt().toString());
    }

    private FeeDtos.PaymentView paymentView(Payment payment, boolean duplicate) {
        Student student = payment.getFeeAccount().getStudent();
        return new FeeDtos.PaymentView(payment.getId(), payment.getReceiptNo(), payment.getReference(),
                payment.getAmount(), payment.getMethod().name(), payment.getStatus().name(),
                payment.getPaidAt().toString(), student.getName(), student.getRollNo(),
                student.getProgramme().getCode(), payment.getFeeAccount().getAcademicYear(),
                payment.getRecordedBy(), duplicate);
    }

    private String nextReceiptNo() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "C1-" + calendar.today().getYear() + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            if (!paymentRepository.existsByReceiptNo(candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Could not generate a receipt number. Please retry.");
    }

    private BigDecimal sum(List<FeeDtos.FeeRow> rows, java.util.function.Function<FeeDtos.FeeRow, BigDecimal> mapper) {
        return rows.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String csvText(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
