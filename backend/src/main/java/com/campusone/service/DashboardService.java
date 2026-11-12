package com.campusone.service;

import com.campusone.api.dto.DashboardDtos;
import com.campusone.api.dto.UserView;
import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.AttendanceSession;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.MarkEntry;
import com.campusone.domain.MarksComponent;
import com.campusone.domain.MentorTask;
import com.campusone.domain.ModerationState;
import com.campusone.domain.RiskFlag;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.domain.TeachingSlot;
import com.campusone.repo.AttendanceRecordRepository;
import com.campusone.repo.AttendanceSessionRepository;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.MarkEntryRepository;
import com.campusone.repo.MarksComponentRepository;
import com.campusone.repo.MentorTaskRepository;
import com.campusone.repo.ModerationStateRepository;
import com.campusone.repo.RiskFlagRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.TeachingSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final AccessService accessService;
    private final AcademicMetricsService metricsService;
    private final AcademicCalendar calendar;
    private final TeachingSlotRepository teachingSlotRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final SubjectRepository subjectRepository;
    private final MarksComponentRepository componentRepository;
    private final MarkEntryRepository markEntryRepository;
    private final ModerationStateRepository moderationStateRepository;
    private final FeeAccountRepository feeAccountRepository;
    private final RiskFlagRepository riskFlagRepository;
    private final MentorTaskRepository mentorTaskRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;
    private final int threshold;

    public DashboardService(AccessService accessService, AcademicMetricsService metricsService,
                            AcademicCalendar calendar, TeachingSlotRepository teachingSlotRepository,
                            AttendanceSessionRepository sessionRepository,
                            AttendanceRecordRepository recordRepository, SubjectRepository subjectRepository,
                            MarksComponentRepository componentRepository, MarkEntryRepository markEntryRepository,
                            ModerationStateRepository moderationStateRepository,
                            FeeAccountRepository feeAccountRepository, RiskFlagRepository riskFlagRepository,
                            MentorTaskRepository mentorTaskRepository, StudentRepository studentRepository, Clock clock,
                            @Value("${app.attendance.threshold-percent:75}") int threshold) {
        this.accessService = accessService;
        this.metricsService = metricsService;
        this.calendar = calendar;
        this.teachingSlotRepository = teachingSlotRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.subjectRepository = subjectRepository;
        this.componentRepository = componentRepository;
        this.markEntryRepository = markEntryRepository;
        this.moderationStateRepository = moderationStateRepository;
        this.feeAccountRepository = feeAccountRepository;
        this.riskFlagRepository = riskFlagRepository;
        this.mentorTaskRepository = mentorTaskRepository;
        this.studentRepository = studentRepository;
        this.clock = clock;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.View dashboard() {
        var user = accessService.user();
        boolean admin = user.isAdmin();
        LocalDate today = calendar.today();
        List<Student> students = accessService.visibleStudents();
        Set<Long> studentIds = students.stream().map(Student::getId).collect(Collectors.toSet());
        Set<Long> programmeIds = accessService.visibleProgrammeIds();
        var snapshot = metricsService.snapshot();

        List<TeachingSlot> slots = admin
                ? teachingSlotRepository.findAll()
                : teachingSlotRepository.findByFacultyIdOrderByPeriod(user.id());
        List<TeachingSlot> todaySlots = slots.stream()
                .filter(slot -> slot.getDayOfWeek() == today.getDayOfWeek())
                .sorted(Comparator.comparingInt(TeachingSlot::getPeriod))
                .toList();
        List<DashboardDtos.TodayClass> classes = new ArrayList<>();
        int submittedCount = 0;
        for (TeachingSlot slot : todaySlots) {
            Subject subject = slot.getSubject();
            var sessionOpt = sessionRepository.findBySubjectIdAndDateAndPeriod(subject.getId(), today, slot.getPeriod());
            Integer present = null;
            Integer total = null;
            Double pct = null;
            if (sessionOpt.isPresent()) {
                List<AttendanceRecord> records = recordRepository.findBySessionId(sessionOpt.get().getId());
                present = (int) records.stream().filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT).count();
                total = records.size();
                pct = total == 0 ? null : round1(present * 100.0 / total);
                submittedCount++;
            }
            classes.add(new DashboardDtos.TodayClass(subject.getId(), subject.getCode(), subject.getName(),
                    subject.getProgramme().getCode(), subject.getStudyYear(), slot.getPeriod(), slot.getRoom(),
                    slot.getFaculty().getName(), sessionOpt.isPresent(), present, total, pct));
        }

        List<RiskFlag> scopedFlags = riskFlagRepository.findAll().stream()
                .filter(f -> programmeIds.contains(f.getStudent().getProgramme().getId()))
                .toList();
        List<RiskFlag> openFlags = scopedFlags.stream()
                .filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED)
                .sorted(Comparator.comparingInt(RiskFlag::getScore).reversed())
                .toList();
        Set<Long> scopedFlagIds = scopedFlags.stream().map(RiskFlag::getId).collect(Collectors.toSet());
        List<MentorTask> openTasks = mentorTaskRepository.findByStatusOrderByDueDateAsc(MentorTask.Status.OPEN).stream()
                .filter(t -> scopedFlagIds.contains(t.getRiskFlag().getId()))
                .toList();

        List<DashboardDtos.RiskPreview> risks = openFlags.stream().limit(4)
                .map(f -> new DashboardDtos.RiskPreview(f.getId(), f.getStudent().getId(),
                        f.getStudent().getName(), f.getStudent().getRollNo(),
                        f.getStudent().getProgramme().getCode(), f.getScore(), f.getBand().name(),
                        f.getReasons().split("\n")[0]))
                .toList();

        List<FeeAccount> accounts = admin ? feeAccountRepository.findAllByOrderByDueAmountDesc() : List.of();
        BigDecimal feeOutstanding = accounts.stream().map(FeeAccount::getDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int defaulters = (int) accounts.stream().filter(a -> a.getDueAmount().signum() > 0).count();
        int reminderCandidates = (int) accounts.stream().filter(this::reminderDue).count();
        String lastReminder = accounts.stream().map(FeeAccount::getLastReminderAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).map(Instant::toString).orElse(null);
        List<DashboardDtos.DuePreview> dues = accounts.stream()
                .filter(a -> a.getDueAmount().signum() > 0).limit(4)
                .map(a -> new DashboardDtos.DuePreview(a.getStudent().getId(), a.getStudent().getName(),
                        a.getStudent().getRollNo(), a.getStudent().getProgramme().getCode(), a.getDueAmount(),
                        a.getDueDate().toString(),
                        a.getDueDate().isBefore(today) ? "OVERDUE" : "DUE"))
                .toList();

        List<DashboardDtos.WeekPoint> trend = weeklyTrend(studentIds);

        int belowThreshold = (int) students.stream().map(s -> snapshot.get(s.getId()))
                .filter(m -> m != null && m.attendancePercent() != null && m.attendancePercent() < threshold)
                .count();
        List<DashboardDtos.SubjectPreview> pendingModeration = pendingModeration(admin, user.id(), programmeIds);
        Double thisWeek = trend.isEmpty() ? null : trend.get(trend.size() - 1).attendancePercent();
        String latestAlert = openFlags.stream().map(RiskFlag::getComputedAt).max(Comparator.naturalOrder())
                .map(Instant::toString).orElse(null);

        String scopeNote = admin ? "All programmes · " + user.campusName()
                : "Your teaching scope · " + user.campusName();

        var metrics = new DashboardDtos.Metrics(
                students.size(), todaySlots.size(), submittedCount, thisWeek, belowThreshold,
                feeOutstanding, defaulters, openFlags.size(), openTasks.size(), pendingModeration.size());
        var automation = new DashboardDtos.Automation(reminderCandidates, lastReminder, openFlags.size(), latestAlert);

        return new DashboardDtos.View(UserView.from(user), today.toString(), calendar.todayLabel(),
                calendar.academicYear(), scopeNote, metrics, classes, risks, dues, trend, automation,
                pendingModeration);
    }

    private List<DashboardDtos.WeekPoint> weeklyTrend(Set<Long> studentIds) {
        LocalDate weekStart = calendar.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, List<AttendanceRecord>> byWeek = new LinkedHashMap<>();
        for (int i = 3; i >= 0; i--) {
            byWeek.put(weekStart.minusWeeks(i), new ArrayList<>());
        }
        for (AttendanceRecord record : recordRepository.findAllSubmitted()) {
            if (!studentIds.contains(record.getStudent().getId())) {
                continue;
            }
            LocalDate sessionWeek = record.getSession().getDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (byWeek.containsKey(sessionWeek)) {
                byWeek.get(sessionWeek).add(record);
            }
        }
        List<DashboardDtos.WeekPoint> points = new ArrayList<>();
        byWeek.forEach((week, records) -> {
            if (records.isEmpty()) {
                points.add(new DashboardDtos.WeekPoint(week.format(WEEK_LABEL), null, 0));
            } else {
                long present = records.stream().filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT).count();
                int sessions = (int) records.stream().map(r -> r.getSession().getId()).distinct().count();
                points.add(new DashboardDtos.WeekPoint(week.format(WEEK_LABEL),
                        round1(present * 100.0 / records.size()), sessions));
            }
        });
        return points;
    }

    private List<DashboardDtos.SubjectPreview> pendingModeration(boolean admin, Long userId, Set<Long> programmeIds) {
        List<Subject> subjects;
        if (admin) {
            subjects = subjectRepository.findAll().stream()
                    .filter(s -> programmeIds.contains(s.getProgramme().getId()))
                    .sorted(Comparator.comparing(Subject::getCode)).toList();
        } else {
            subjects = teachingSlotRepository.findByFacultyIdOrderByPeriod(userId).stream()
                    .map(TeachingSlot::getSubject).distinct()
                    .sorted(Comparator.comparing(Subject::getCode)).toList();
        }
        List<DashboardDtos.SubjectPreview> result = new ArrayList<>();
        for (Subject subject : subjects) {
            List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subject.getId());
            if (components.isEmpty()) {
                continue;
            }
            ModerationState state = moderationStateRepository.findBySubjectId(subject.getId()).orElse(null);
            if (state != null && state.getStatus() == ModerationState.Status.LOCKED) {
                continue;
            }
            List<MarkEntry> entries = markEntryRepository.findDetailedBySubject(subject.getId());
            int expected = studentRepositoryCount(subject) * components.size();
            result.add(new DashboardDtos.SubjectPreview(subject.getId(), subject.getCode(), subject.getName(),
                    subject.getProgramme().getCode(), state == null ? "DRAFT" : state.getStatus().name(),
                    entries.size(), expected));
        }
        return result;
    }

    private int studentRepositoryCount(Subject subject) {
        return studentRepository.findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(
                subject.getProgramme().getId(), subject.getStudyYear()).size();
    }

    private boolean reminderDue(FeeAccount account) {
        if (account.getDueAmount().signum() <= 0) {
            return false;
        }
        Instant startOfToday = calendar.today().atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant();
        return account.getLastReminderAt() == null || account.getLastReminderAt().isBefore(startOfToday);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
