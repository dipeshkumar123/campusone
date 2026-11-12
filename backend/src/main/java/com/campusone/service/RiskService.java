package com.campusone.service;

import com.campusone.api.dto.RiskDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.MentorTask;
import com.campusone.domain.RiskFlag;
import com.campusone.domain.Student;
import com.campusone.domain.TeachingSlot;
import com.campusone.domain.User;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.MentorTaskRepository;
import com.campusone.repo.RiskFlagRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.TeachingSlotRepository;
import com.campusone.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RiskService {
    private final AccessService accessService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeachingSlotRepository teachingSlotRepository;
    private final RiskFlagRepository riskFlagRepository;
    private final MentorTaskRepository mentorTaskRepository;
    private final FeeAccountRepository feeAccountRepository;
    private final AcademicMetricsService metricsService;
    private final RiskScoringModel scoringModel;
    private final AcademicCalendar calendar;
    private final AuditService auditService;
    private final Clock clock;

    public RiskService(AccessService accessService, StudentRepository studentRepository, UserRepository userRepository,
                       TeachingSlotRepository teachingSlotRepository, RiskFlagRepository riskFlagRepository,
                       MentorTaskRepository mentorTaskRepository, FeeAccountRepository feeAccountRepository,
                       AcademicMetricsService metricsService, RiskScoringModel scoringModel,
                       AcademicCalendar calendar, AuditService auditService, Clock clock) {
        this.accessService = accessService;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.teachingSlotRepository = teachingSlotRepository;
        this.riskFlagRepository = riskFlagRepository;
        this.mentorTaskRepository = mentorTaskRepository;
        this.feeAccountRepository = feeAccountRepository;
        this.metricsService = metricsService;
        this.scoringModel = scoringModel;
        this.calendar = calendar;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RiskDtos.PageView page(String statusFilter) {
        var user = accessService.user();
        List<RiskFlag> flags;
        String scopeNote;
        if (user.isAdmin()) {
            flags = riskFlagRepository.findAllByOrderByScoreDesc();
            scopeNote = "All programmes at " + user.campusName();
        } else {
            Set<Long> programmeIds = accessService.visibleProgrammeIds();
            flags = programmeIds.isEmpty() ? List.of() : riskFlagRepository.findForProgrammes(programmeIds);
            scopeNote = flags.stream().map(f -> f.getStudent().getProgramme().getCode()).distinct().sorted()
                    .collect(Collectors.joining(", "));
            scopeNote = scopeNote.isBlank() ? "Your teaching scope" : "Your programmes: " + scopeNote;
        }

        List<RiskFlag> allInScope = flags;
        int open = (int) allInScope.stream().filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED).count();
        int high = (int) allInScope.stream().filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED
                && f.getBand() == RiskFlag.Band.HIGH).count();
        int medium = (int) allInScope.stream().filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED
                && f.getBand() == RiskFlag.Band.MEDIUM).count();
        List<MentorTask> tasks = mentorTaskRepository.findByStatusOrderByDueDateAsc(MentorTask.Status.OPEN);
        Set<Long> scopedFlagIds = allInScope.stream().map(RiskFlag::getId).collect(Collectors.toSet());
        int tasksOpen = (int) tasks.stream().filter(t -> scopedFlagIds.contains(t.getRiskFlag().getId())).count();
        String computedAt = allInScope.stream().map(RiskFlag::getComputedAt).max(Comparator.naturalOrder())
                .map(Object::toString).orElse(null);

        String normalized = statusFilter == null ? "OPEN" : statusFilter.trim().toUpperCase();
        List<RiskFlag> filtered = allInScope.stream().filter(flag -> switch (normalized) {
            case "ALL" -> true;
            case "RESOLVED" -> flag.getStatus() == RiskFlag.Status.RESOLVED;
            default -> flag.getStatus() != RiskFlag.Status.RESOLVED;
        }).sorted(Comparator.comparingInt(RiskFlag::getScore).reversed()).toList();

        Map<Long, FeeAccount> fees = new HashMap<>();
        if (user.isAdmin()) {
            feeAccountRepository.findAll().forEach(account -> fees.put(account.getStudent().getId(), account));
        }
        List<RiskDtos.RiskItem> items = filtered.stream().map(flag -> toItem(flag, fees)).toList();
        return new RiskDtos.PageView(new RiskDtos.Summary(open, high, medium, tasksOpen, computedAt, scopeNote), items);
    }

    @Transactional
    public RiskDtos.RecomputeResult recompute() {
        accessService.requireAdmin();
        return recomputeInternal();
    }

    @Transactional
    public RiskDtos.RecomputeResult recomputeInternal() {
        var snapshot = metricsService.snapshot();
        List<RiskFlag> existingFlags = riskFlagRepository.findAll();
        Map<Long, RiskFlag> byStudent = existingFlags.stream()
                .collect(Collectors.toMap(f -> f.getStudent().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<TeachingSlot>> slotsByProgramme = teachingSlotRepository.findAll().stream()
                .collect(Collectors.groupingBy(slot -> slot.getSubject().getProgramme().getId()));

        int high = 0;
        int medium = 0;
        int resolved = 0;
        int created = 0;
        int tasksCreated = 0;
        List<RiskFlag> toSave = new ArrayList<>();

        for (var metric : snapshot.students()) {
            Double attendance = metric.attendancePercent();
            Double marks = metric.marksPercent();
            if (attendance == null && marks == null) {
                continue;
            }
            RiskScoringModel.Assessment assessment = scoringModel.assess(attendance, marks, metric.recentTrendDelta());
            RiskFlag flag = byStudent.get(metric.student().getId());
            if (assessment.score() >= 50) {
                boolean isNew = flag == null;
                if (flag == null) {
                    flag = new RiskFlag(metric.student(), assessment.score(),
                            RiskFlag.Band.valueOf(assessment.band()),
                            String.join("\n", assessment.reasons()),
                            BigDecimal.valueOf(zeroIfNull(attendance)),
                            BigDecimal.valueOf(zeroIfNull(marks)),
                            BigDecimal.valueOf(metric.recentTrendDelta()));
                    created++;
                } else {
                    flag.setScore(assessment.score());
                    flag.setBand(RiskFlag.Band.valueOf(assessment.band()));
                    flag.setReasons(String.join("\n", assessment.reasons()));
                    flag.setAttendancePercent(BigDecimal.valueOf(zeroIfNull(attendance)));
                    flag.setMarksPercent(BigDecimal.valueOf(zeroIfNull(marks)));
                    flag.setTrendDelta(BigDecimal.valueOf(metric.recentTrendDelta()));
                    if (flag.getStatus() == RiskFlag.Status.RESOLVED) {
                        flag.setStatus(RiskFlag.Status.OPEN);
                    }
                }
                flag.setComputedAt(clock.instant());
                toSave.add(flag);
                if (isNew) {
                    riskFlagRepository.save(flag);
                    byStudent.put(metric.student().getId(), flag);
                }
                if (assessment.band().equals("HIGH")) {
                    high++;
                    if (isNew && autoTask(flag, slotsByProgramme)) {
                        tasksCreated++;
                    }
                } else {
                    medium++;
                }
            } else if (flag != null && flag.getStatus() == RiskFlag.Status.OPEN) {
                flag.setStatus(RiskFlag.Status.RESOLVED);
                flag.setScore(assessment.score());
                flag.setBand(RiskFlag.Band.LOW);
                flag.setReasons(String.join("\n", assessment.reasons()));
                flag.setComputedAt(clock.instant());
                toSave.add(flag);
                resolved++;
            }
        }
        riskFlagRepository.saveAll(toSave);
        int flagged = high + medium;
        auditService.log("RISK_RECOMPUTED", "RISK_FLAG", "batch",
                flagged + " current flags · " + high + " high · " + medium + " medium · "
                        + resolved + " resolved · " + tasksCreated + " mentor tasks auto-created");
        return new RiskDtos.RecomputeResult(flagged, high, medium, resolved, tasksCreated,
                flagged == 0 ? "No students crossed the risk threshold." :
                        flagged + " students are on the watchlist. High-risk flags create a mentor task only — no marks, fees or attendance records are changed.");
    }

    @Transactional
    public RiskDtos.TaskResponse createTask(Long flagId, RiskDtos.CreateTaskRequest request) {
        RiskFlag flag = riskFlagRepository.findById(flagId)
                .orElseThrow(() -> ApiException.notFound("Risk flag not found."));
        accessService.assertCanView(flag.getStudent());
        if (flag.getStatus() == RiskFlag.Status.RESOLVED) {
            throw ApiException.conflict("This flag is resolved and no longer needs a mentor task.");
        }
        boolean hasOpen = mentorTaskRepository.findByRiskFlagIdOrderByCreatedAtDesc(flagId).stream()
                .anyMatch(t -> t.getStatus() == MentorTask.Status.OPEN);
        if (hasOpen) {
            throw ApiException.conflict("An open mentor task already exists for this student.");
        }
        User assignee = userRepository.findById(request.assigneeId())
                .filter(u -> u.isActive() && u.getRole() == User.Role.FACULTY)
                .orElseThrow(() -> ApiException.badRequest("Choose an active faculty mentor."));
        LocalDate due = request.dueDate();
        if (due.isBefore(calendar.today()) || due.isAfter(calendar.today().plusDays(60))) {
            throw ApiException.badRequest("Due date must be within the next 60 days.");
        }
        MentorTask task = new MentorTask(flag, flag.getStudent(), assignee, request.note().trim(), due,
                accessService.user().name());
        mentorTaskRepository.save(task);
        flag.setStatus(RiskFlag.Status.ACKNOWLEDGED);
        riskFlagRepository.save(flag);
        auditService.log("MENTOR_TASK_CREATED", "MENTOR_TASK", task.getId().toString(),
                flag.getStudent().getRollNo() + " · assigned to " + assignee.getName() + " · due " + due);
        return new RiskDtos.TaskResponse(taskView(task), "Mentor task created.");
    }

    @Transactional
    public RiskDtos.TaskResponse completeTask(Long taskId) {
        MentorTask task = mentorTaskRepository.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("Mentor task not found."));
        var user = accessService.user();
        if (!user.isAdmin() && !task.getAssignee().getId().equals(user.id())) {
            throw ApiException.forbidden("Only the assigned mentor or an office admin can complete this task.");
        }
        if (task.getStatus() == MentorTask.Status.DONE) {
            return new RiskDtos.TaskResponse(taskView(task), "Task is already done.");
        }
        task.setStatus(MentorTask.Status.DONE);
        mentorTaskRepository.save(task);
        auditService.log("MENTOR_TASK_COMPLETED", "MENTOR_TASK", task.getId().toString(),
                task.getStudent().getRollNo() + " · " + task.getAssignee().getName());
        return new RiskDtos.TaskResponse(taskView(task), "Mentor task marked done.");
    }

    private boolean autoTask(RiskFlag flag, Map<Long, List<TeachingSlot>> slotsByProgramme) {
        List<TeachingSlot> slots = slotsByProgramme.getOrDefault(flag.getStudent().getProgramme().getId(), List.of());
        if (slots.isEmpty()) {
            return false;
        }
        Optional<TeachingSlot> preferred = slots.stream()
                .filter(slot -> slot.getSubject().getStudyYear() == flag.getStudent().getStudyYear())
                .findFirst();
        User assignee = preferred.orElse(slots.get(0)).getFaculty();
        MentorTask task = new MentorTask(flag, flag.getStudent(), assignee,
                "Auto-created from a high at-risk flag. Check in about attendance and internal marks.",
                calendar.today().plusDays(7), "CampusOne automation");
        mentorTaskRepository.save(task);
        flag.setStatus(RiskFlag.Status.ACKNOWLEDGED);
        return true;
    }

    private RiskDtos.RiskItem toItem(RiskFlag flag, Map<Long, FeeAccount> fees) {
        Student student = flag.getStudent();
        List<MentorTask> tasks = mentorTaskRepository.findByRiskFlagIdOrderByCreatedAtDesc(flag.getId());
        MentorTask latest = tasks.isEmpty() ? null : tasks.get(0);
        FeeAccount account = fees.get(student.getId());
        return new RiskDtos.RiskItem(flag.getId(), student.getId(), student.getName(), student.getRollNo(),
                student.getProgramme().getCode(), student.getProgramme().getName(), student.getStudyYear(),
                flag.getScore(), flag.getBand().name(), flag.getStatus().name(),
                Arrays.stream(flag.getReasons().split("\n")).filter(s -> !s.isBlank()).toList(),
                flag.getAttendancePercent(), flag.getMarksPercent(), flag.getTrendDelta(),
                account == null ? null : account.getDueAmount(),
                latest == null ? null : taskView(latest), flag.getComputedAt().toString());
    }

    private RiskDtos.MentorTaskView taskView(MentorTask task) {
        return new RiskDtos.MentorTaskView(task.getId(), task.getAssignee().getId(), task.getAssignee().getName(),
                task.getNote(), task.getDueDate().toString(), task.getStatus().name(), task.getCreatedBy(),
                task.getCreatedAt().toString());
    }

    private double zeroIfNull(Double value) {
        return value == null ? 0 : value;
    }
}
