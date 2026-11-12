package com.campusone.service;

import com.campusone.api.dto.AssistantDtos;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.RiskFlag;
import com.campusone.domain.Student;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.RiskFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AssistantService {
    private static final int PREVIEW_LIMIT = 50;

    private final AccessService accessService;
    private final AcademicMetricsService metricsService;
    private final FeeAccountRepository feeAccountRepository;
    private final RiskFlagRepository riskFlagRepository;
    private final QuestionParser parser;
    private final AcademicCalendar calendar;
    private final Clock clock;

    public AssistantService(AccessService accessService, AcademicMetricsService metricsService,
                            FeeAccountRepository feeAccountRepository, RiskFlagRepository riskFlagRepository,
                            QuestionParser parser, AcademicCalendar calendar, Clock clock) {
        this.accessService = accessService;
        this.metricsService = metricsService;
        this.feeAccountRepository = feeAccountRepository;
        this.riskFlagRepository = riskFlagRepository;
        this.parser = parser;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssistantDtos.Answer ask(AssistantDtos.QuestionRequest request) {
        accessService.requireAdmin();
        QuestionParser.ParsedQuestion parsed = parser.parse(request.question());
        List<String> examples = QuestionParser.defaultExamples();
        String scopeNote = "Read-only. No records were changed. Scope: " + accessService.user().campusName()
                + " · " + accessService.visibleStudents().size() + " active students. Generated "
                + calendar.todayLabel() + ".";

        if (parsed.refused()) {
            return new AssistantDtos.Answer("REFUSED", parsed.refusalMessage(), "No query was run.",
                    List.of(), 0, List.of(), List.of(), "No query executed — help or clarification only.",
                    scopeNote, examples, List.of(), true, clock.instant().toString());
        }
        if (parsed.clarification()) {
            return new AssistantDtos.Answer("CLARIFY", parsed.clarificationMessage(), "No query was run.",
                    List.of(), 0, List.of(), List.of(), "No query executed — clarification needed.",
                    scopeNote, examples, parsed.clarificationOptions(), true, clock.instant().toString());
        }

        List<Student> students = accessService.visibleStudents();
        var snapshot = metricsService.snapshot();
        Map<Long, RiskFlag> openFlags = new HashMap<>();
        riskFlagRepository.findAll().stream()
                .filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED)
                .forEach(f -> openFlags.put(f.getStudent().getId(), f));
        Map<Long, FeeAccount> fees = new HashMap<>();
        feeAccountRepository.findAll().forEach(account -> fees.put(account.getStudent().getId(), account));
        Map<String, String> programmeNames = students.stream().collect(Collectors.toMap(
                s -> s.getProgramme().getCode(), s -> s.getProgramme().getName(), (a, b) -> a));

        List<Match> matches = new ArrayList<>();
        for (Student student : students) {
            if (parsed.programmeCode() != null && !student.getProgramme().getCode().equals(parsed.programmeCode())) {
                continue;
            }
            if (parsed.studyYear() != null && student.getStudyYear() != parsed.studyYear()) {
                continue;
            }
            var metric = snapshot.get(student.getId());
            Double attendance = null;
            Double marksPercent = null;
            Double marksOutOf40 = null;
            if (metric != null) {
                attendance = parsed.thisMonth() ? metric.monthAttendance().percent() : metric.attendancePercent();
                marksPercent = metric.marksPercent();
                marksOutOf40 = metric.marks().outOf40();
            }
            if (parsed.attendanceBelow() != null
                    && (attendance == null || attendance >= parsed.attendanceBelow())) {
                continue;
            }
            if (parsed.marksBelowPercent() != null
                    && (marksPercent == null || marksPercent >= parsed.marksBelowPercent())) {
                continue;
            }
            FeeAccount fee = fees.get(student.getId());
            BigDecimal due = fee == null ? null : fee.getDueAmount();
            if (parsed.dueAbove() != null) {
                if (due == null || due.compareTo(parsed.dueAbove()) <= 0) {
                    continue;
                }
            }
            RiskFlag flag = openFlags.get(student.getId());
            if (parsed.atRisk() && flag == null) {
                continue;
            }
            if (parsed.consentPending() && student.getConsentStatus() != Student.ConsentStatus.PENDING) {
                continue;
            }
            matches.add(new Match(student, attendance, marksPercent, marksOutOf40, due, flag));
        }
        matches.sort(Comparator.comparing(m -> m.student().getName()));

        boolean broad = parsed.attendanceBelow() == null && parsed.marksBelowPercent() == null
                && parsed.dueAbove() == null && !parsed.atRisk() && !parsed.consentPending()
                && !parsed.averageMarks();
        boolean showAttendance = broad || parsed.attendanceBelow() != null || parsed.atRisk();
        boolean showMarks = broad || parsed.marksBelowPercent() != null || parsed.averageMarks() || parsed.atRisk();
        boolean showFees = broad || parsed.dueAbove() != null;
        boolean showRisk = broad || parsed.atRisk();
        boolean showConsent = parsed.consentPending();

        LinkedHashMap<String, AssistantDtos.Column> columns = new LinkedHashMap<>();
        columns.put("rollNo", new AssistantDtos.Column("rollNo", "Roll no.", "text"));
        columns.put("name", new AssistantDtos.Column("name", "Student", "text"));
        columns.put("programme", new AssistantDtos.Column("programme", "Programme", "text"));
        columns.put("year", new AssistantDtos.Column("year", "Year", "number"));
        if (showAttendance) {
            columns.put("attendance", new AssistantDtos.Column("attendance", parsed.thisMonth()
                    ? "Attendance · month" : "Attendance · term", "percent"));
        }
        if (showMarks) {
            columns.put("internalMarks", new AssistantDtos.Column("internalMarks", "Internal · /40", "number"));
        }
        if (showFees) {
            columns.put("feeDue", new AssistantDtos.Column("feeDue", "Fee due", "currency"));
        }
        if (showRisk) {
            columns.put("risk", new AssistantDtos.Column("risk", "Risk", "status"));
        }
        if (showConsent) {
            columns.put("consent", new AssistantDtos.Column("consent", "Consent", "status"));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Match match : matches.subList(0, Math.min(PREVIEW_LIMIT, matches.size()))) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("rollNo", match.student().getRollNo());
            row.put("name", match.student().getName());
            row.put("programme", match.student().getProgramme().getCode());
            row.put("year", match.student().getStudyYear());
            if (showAttendance) {
                row.put("attendance", match.attendance() == null ? null : round1(match.attendance()));
            }
            if (showMarks) {
                row.put("internalMarks", match.marksOutOf40() == null ? null : round1(match.marksOutOf40()));
            }
            if (showFees) {
                row.put("feeDue", match.due());
            }
            if (showRisk) {
                row.put("risk", match.flag() == null ? "—"
                        : match.flag().getBand().name().charAt(0)
                        + match.flag().getBand().name().substring(1).toLowerCase(Locale.ENGLISH)
                        + " · " + match.flag().getScore());
            }
            if (showConsent) {
                row.put("consent", match.student().getConsentStatus() == Student.ConsentStatus.PENDING
                        ? "Pending" : "Consented");
            }
            rows.add(row);
        }

        String summary;
        if (matches.isEmpty()) {
            summary = "No students match those filters. Try widening the year, programme or cutoff.";
        } else if (parsed.averageMarks()) {
            double avgPercent = matches.stream().filter(m -> m.marksPercent() != null)
                    .mapToDouble(Match::marksPercent).average().orElse(0);
            double avgOutOf40 = matches.stream().filter(m -> m.marksOutOf40() != null)
                    .mapToDouble(Match::marksOutOf40).average().orElse(0);
            summary = "Average internal marks: " + format1(avgPercent) + "% ("
                    + format1(avgOutOf40) + " / 40) across " + matches.size() + " student"
                    + (matches.size() == 1 ? "" : "s") + ".";
        } else if (parsed.countOnly()) {
            summary = matches.size() + " student" + (matches.size() == 1 ? " matches." : "s match.");
        } else {
            summary = matches.size() + " student" + (matches.size() == 1 ? " matches." : "s match.")
                    + (matches.size() > PREVIEW_LIMIT ? " Showing the first " + PREVIEW_LIMIT + "." : "");
        }

        List<AssistantDtos.FilterChip> filters = buildFilters(parsed, programmeNames);
        String interpretation = buildInterpretation(parsed, programmeNames);
        String plan = buildPlan(parsed);

        return new AssistantDtos.Answer("ANSWERED", summary, interpretation, filters, matches.size(),
                new ArrayList<>(columns.values()), rows, plan, scopeNote, examples, List.of(), true,
                clock.instant().toString());
    }

    private List<AssistantDtos.FilterChip> buildFilters(QuestionParser.ParsedQuestion parsed,
                                                        Map<String, String> programmeNames) {
        List<AssistantDtos.FilterChip> filters = new ArrayList<>();
        if (parsed.programmeCode() != null) {
            filters.add(new AssistantDtos.FilterChip("Programme",
                    programmeNames.getOrDefault(parsed.programmeCode(), parsed.programmeCode())));
        }
        if (parsed.studyYear() != null) {
            filters.add(new AssistantDtos.FilterChip("Year", ordinalYear(parsed.studyYear())));
        }
        if (parsed.attendanceBelow() != null) {
            filters.add(new AssistantDtos.FilterChip("Attendance",
                    "Below " + format1(parsed.attendanceBelow()) + "%"
                            + (parsed.thisMonth() ? " · this month" : " · term")));
        }
        if (parsed.marksBelowPercent() != null) {
            filters.add(new AssistantDtos.FilterChip("Internal marks",
                    "Below " + parsed.marksFilterLabel()));
        }
        if (parsed.dueAbove() != null) {
            filters.add(new AssistantDtos.FilterChip("Fees",
                    parsed.dueAbove().signum() == 0 ? "Any outstanding dues"
                            : "Above ₹" + parsed.dueAbove().toPlainString()));
        }
        if (parsed.atRisk()) {
            filters.add(new AssistantDtos.FilterChip("Risk", "Open at-risk flags"));
        }
        if (parsed.consentPending()) {
            filters.add(new AssistantDtos.FilterChip("Consent", "Guardian consent pending"));
        }
        for (String note : parsed.notes()) {
            filters.add(new AssistantDtos.FilterChip("Read as", note));
        }
        return filters;
    }

    private String buildInterpretation(QuestionParser.ParsedQuestion parsed, Map<String, String> programmeNames) {
        List<String> parts = new ArrayList<>();
        if (parsed.programmeCode() != null) {
            parts.add(programmeNames.getOrDefault(parsed.programmeCode(), parsed.programmeCode()));
        }
        if (parsed.studyYear() != null) {
            parts.add(ordinalYear(parsed.studyYear()));
        }
        if (parsed.attendanceBelow() != null) {
            parts.add("attendance below " + format1(parsed.attendanceBelow()) + "%"
                    + (parsed.thisMonth() ? " this month" : ""));
        }
        if (parsed.marksBelowPercent() != null) {
            parts.add("internal marks below " + parsed.marksFilterLabel());
        }
        if (parsed.dueAbove() != null) {
            parts.add(parsed.dueAbove().signum() == 0 ? "with fee dues" : "fee dues above ₹"
                    + parsed.dueAbove().toPlainString());
        }
        if (parsed.atRisk()) {
            parts.add("on the at-risk watchlist");
        }
        if (parsed.consentPending()) {
            parts.add("with guardian consent pending");
        }
        if (parts.isEmpty()) {
            parts.add("all active students");
        }
        return "Students · " + String.join(" · ", parts);
    }

    private String buildPlan(QuestionParser.ParsedQuestion parsed) {
        List<String> where = new ArrayList<>();
        if (parsed.programmeCode() != null) {
            where.add("programme_code = '" + parsed.programmeCode() + "'");
        }
        if (parsed.studyYear() != null) {
            where.add("study_year = " + parsed.studyYear());
        }
        if (parsed.attendanceBelow() != null) {
            where.add((parsed.thisMonth() ? "month_attendance_pct" : "term_attendance_pct") + " < "
                    + format1(parsed.attendanceBelow()));
        }
        if (parsed.marksBelowPercent() != null) {
            where.add("internal_marks_pct < " + format1(parsed.marksBelowPercent()));
        }
        if (parsed.dueAbove() != null) {
            where.add("fee_due > " + parsed.dueAbove().toPlainString());
        }
        if (parsed.atRisk()) {
            where.add("risk_status IN ('OPEN','ACKNOWLEDGED')");
        }
        if (parsed.consentPending()) {
            where.add("consent_status = 'PENDING'");
        }
        return "READ-ONLY · students ⋈ attendance ⋈ internal_marks ⋈ fee_accounts ⋈ risk_flags"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY name LIMIT " + PREVIEW_LIMIT + ";";
    }

    private String ordinalYear(int year) {
        return switch (year) {
            case 1 -> "First year";
            case 2 -> "Second year";
            case 3 -> "Third year";
            case 4 -> "Fourth year";
            default -> "Year " + year;
        };
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String format1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private record Match(Student student, Double attendance, Double marksPercent, Double marksOutOf40,
                         BigDecimal due, RiskFlag flag) {
    }
}
