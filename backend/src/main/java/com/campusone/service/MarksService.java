package com.campusone.service;

import com.campusone.api.dto.MarksDtos;
import com.campusone.common.ApiException;
import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.AuditEvent;
import com.campusone.domain.MarkEntry;
import com.campusone.domain.MarksComponent;
import com.campusone.domain.ModerationState;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.repo.MarkEntryRepository;
import com.campusone.repo.MarksComponentRepository;
import com.campusone.repo.ModerationStateRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarksService {
    private final AccessService accessService;
    private final SubjectRepository subjectRepository;
    private final StudentRepository studentRepository;
    private final MarksComponentRepository componentRepository;
    private final MarkEntryRepository markEntryRepository;
    private final ModerationStateRepository moderationStateRepository;
    private final AcademicMetricsService metricsService;
    private final AuditService auditService;
    private final Clock clock;
    private final int threshold;

    public MarksService(AccessService accessService, SubjectRepository subjectRepository,
                        StudentRepository studentRepository, MarksComponentRepository componentRepository,
                        MarkEntryRepository markEntryRepository, ModerationStateRepository moderationStateRepository,
                        AcademicMetricsService metricsService, AuditService auditService, Clock clock,
                        @Value("${app.attendance.threshold-percent:75}") int threshold) {
        this.accessService = accessService;
        this.subjectRepository = subjectRepository;
        this.studentRepository = studentRepository;
        this.componentRepository = componentRepository;
        this.markEntryRepository = markEntryRepository;
        this.moderationStateRepository = moderationStateRepository;
        this.metricsService = metricsService;
        this.auditService = auditService;
        this.clock = clock;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public List<MarksDtos.SubjectCard> subjects() {
        AuthenticatedUser user = accessService.user();
        List<Subject> subjects;
        if (user.isAdmin()) {
            Set<Long> programmeIds = accessService.visibleProgrammeIds();
            subjects = subjectRepository.findAll().stream()
                    .filter(s -> programmeIds.contains(s.getProgramme().getId()))
                    .sorted(Comparator.comparing(Subject::getCode))
                    .toList();
        } else {
            subjects = accessService.visibleStudents().isEmpty() ? List.of()
                    : subjectRepository.findAll().stream()
                    .filter(s -> accessService.canTeachSubject(s.getId()))
                    .sorted(Comparator.comparing(Subject::getCode))
                    .toList();
        }

        var snapshot = metricsService.snapshot();
        List<MarksDtos.SubjectCard> cards = new ArrayList<>();
        for (Subject subject : subjects) {
            List<Student> students = roster(subject);
            List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subject.getId());
            List<MarkEntry> entries = markEntryRepository.findDetailedBySubject(subject.getId());
            ModerationState state = moderationStateRepository.findBySubjectId(subject.getId()).orElse(null);
            int below = (int) students.stream().filter(s -> {
                var metrics = snapshot.get(s.getId());
                return metrics != null && metrics.attendancePercent() != null
                        && metrics.attendancePercent() < threshold;
            }).count();
            cards.add(new MarksDtos.SubjectCard(subject.getId(), subject.getCode(), subject.getName(),
                    subject.getProgramme().getCode(), subject.getProgramme().getName(), subject.getStudyYear(),
                    state == null ? "DRAFT" : state.getStatus().name(),
                    students.size(), entries.size(), students.size() * components.size(), below));
        }
        return cards;
    }

    @Transactional(readOnly = true)
    public MarksDtos.Sheet sheet(Long subjectId) {
        Subject subject = accessService.requireViewableSubject(subjectId);
        return buildSheet(subject);
    }

    @Transactional
    public MarksDtos.Sheet saveDraft(Long subjectId, MarksDtos.SaveRequest request) {
        Subject subject = accessService.requireTeachSubject(subjectId);
        ModerationState state = moderationState(subject);
        if (state.getStatus() == ModerationState.Status.LOCKED) {
            throw ApiException.conflict("Marks are locked after moderation. Ask an office admin to unlock with a reason.");
        }

        List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subjectId);
        Map<Long, MarksComponent> componentsById = components.stream()
                .collect(Collectors.toMap(MarksComponent::getId, Function.identity()));
        List<Student> roster = roster(subject);
        Map<Long, Student> rosterById = roster.stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<String, MarkEntry> existing = markEntryRepository.findDetailedBySubject(subjectId).stream()
                .collect(Collectors.toMap(e -> key(e.getStudent().getId(), e.getComponent().getId()), Function.identity()));

        Set<String> seen = new HashSet<>();
        List<MarkEntry> toSave = new ArrayList<>();
        for (MarksDtos.SaveEntry entry : request.entries()) {
            MarksComponent component = componentsById.get(entry.componentId());
            Student student = rosterById.get(entry.studentId());
            if (component == null) {
                throw ApiException.badRequest("A marks component does not belong to this subject.");
            }
            if (student == null) {
                throw ApiException.badRequest("A student does not belong to this subject's class.");
            }
            if (entry.score() == null || entry.score().compareTo(BigDecimal.ZERO) < 0
                    || entry.score().compareTo(BigDecimal.valueOf(component.getMaxScore())) > 0) {
                throw ApiException.badRequest("Marks for " + component.getName() + " must be between 0 and "
                        + component.getMaxScore() + ".");
            }
            if (!seen.add(key(student.getId(), component.getId()))) {
                throw ApiException.badRequest("The same student and component appear twice.");
            }
            MarkEntry mark = existing.get(key(student.getId(), component.getId()));
            if (mark == null) {
                mark = new MarkEntry(student, component, entry.score(), accessService.user().name());
                mark.setUpdatedAt(clock.instant());
                markEntryRepository.save(mark);
            } else {
                BigDecimal old = mark.getScore();
                if (old.compareTo(entry.score()) != 0) {
                    auditService.log("MARK_UPDATED", "SUBJECT_MARKS", subject.getId().toString(),
                            student.getRollNo() + " · " + component.getName() + ": " + old.stripTrailingZeros().toPlainString()
                                    + " → " + entry.score().stripTrailingZeros().toPlainString());
                    mark.setScore(entry.score());
                    mark.setUpdatedBy(accessService.user().name());
                    mark.setUpdatedAt(clock.instant());
                    markEntryRepository.save(mark);
                }
            }
            toSave.add(mark);
        }
        auditService.log("MARKS_DRAFT_SAVED", "SUBJECT_MARKS", subject.getId().toString(),
                subject.getCode() + " · " + seen.size() + " marks checked in");
        return buildSheet(subject);
    }

    @Transactional
    public MarksDtos.Sheet lock(Long subjectId, MarksDtos.LockRequest request) {
        accessService.user();
        if (!accessService.user().isAdmin()) {
            throw ApiException.forbidden("Only office admins can lock moderation.");
        }
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject not found."));
        List<Student> roster = roster(subject);
        List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subjectId);
        Map<String, MarkEntry> entries = markEntryRepository.findDetailedBySubject(subjectId).stream()
                .collect(Collectors.toMap(e -> key(e.getStudent().getId(), e.getComponent().getId()), Function.identity()));
        int missing = 0;
        for (Student student : roster) {
            for (MarksComponent component : components) {
                if (!entries.containsKey(key(student.getId(), component.getId()))) {
                    missing++;
                }
            }
        }
        if (missing > 0) {
            throw ApiException.badRequest("Complete all marks before locking. " + missing + " mark"
                    + (missing == 1 ? " is" : "s are") + " still missing.");
        }
        ModerationState state = moderationState(subject);
        if (state.getStatus() == ModerationState.Status.LOCKED) {
            throw ApiException.conflict("These marks are already locked.");
        }
        state.setStatus(ModerationState.Status.LOCKED);
        state.setLockedBy(accessService.user().name());
        state.setLockedAt(clock.instant());
        state.setNote(request == null ? null : request.note());
        state.setUnlockedBy(null);
        state.setUnlockedAt(null);
        state.setUnlockReason(null);
        moderationStateRepository.save(state);
        auditService.log("MODERATION_LOCKED", "SUBJECT_MARKS", subjectId.toString(),
                subject.getCode() + " moderated and locked" + (request != null && request.note() != null
                        && !request.note().isBlank() ? " · " + request.note().trim() : ""));
        return buildSheet(subject);
    }

    @Transactional
    public MarksDtos.Sheet unlock(Long subjectId, MarksDtos.UnlockRequest request) {
        if (!accessService.user().isAdmin()) {
            throw ApiException.forbidden("Only office admins can unlock moderation.");
        }
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject not found."));
        ModerationState state = moderationState(subject);
        if (state.getStatus() != ModerationState.Status.LOCKED) {
            throw ApiException.conflict("These marks are not locked.");
        }
        state.setStatus(ModerationState.Status.DRAFT);
        state.setUnlockedBy(accessService.user().name());
        state.setUnlockedAt(clock.instant());
        state.setUnlockReason(request.reason().trim());
        moderationStateRepository.save(state);
        auditService.log("MODERATION_UNLOCKED", "SUBJECT_MARKS", subjectId.toString(),
                subject.getCode() + " unlocked · " + request.reason().trim());
        return buildSheet(subject);
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long subjectId) {
        Subject subject = accessService.requireViewableSubject(subjectId);
        List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subjectId);
        List<Student> roster = roster(subject);
        Map<String, MarkEntry> entries = markEntryRepository.findDetailedBySubject(subjectId).stream()
                .collect(Collectors.toMap(e -> key(e.getStudent().getId(), e.getComponent().getId()), Function.identity()));
        var snapshot = metricsService.snapshot();

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("ROLL_NO,STUDENT_NAME,PROGRAMME,YEAR,SUBJECT_CODE,SUBJECT_NAME");
        for (MarksComponent component : components) {
            csv.append(',').append(csvText(component.getName() + " (" + component.getMaxScore() + ")"));
        }
        csv.append(",TOTAL_40,ATTENDANCE_PCT\n");
        for (Student student : roster) {
            csv.append(csvText(student.getRollNo())).append(',')
                    .append(csvText(student.getName())).append(',')
                    .append(csvText(student.getProgramme().getCode())).append(',')
                    .append(student.getStudyYear()).append(',')
                    .append(csvText(subject.getCode())).append(',')
                    .append(csvText(subject.getName()));
            BigDecimal total = BigDecimal.ZERO;
            for (MarksComponent component : components) {
                MarkEntry entry = entries.get(key(student.getId(), component.getId()));
                BigDecimal score = entry == null ? null : entry.getScore();
                csv.append(',').append(score == null ? "" : score.stripTrailingZeros().toPlainString());
                if (score != null) {
                    total = total.add(score);
                }
            }
            csv.append(',')
                    .append(total.stripTrailingZeros().toPlainString()).append(',');
            var metrics = snapshot.get(student.getId());
            if (metrics != null && metrics.attendancePercent() != null) {
                csv.append(metrics.attendancePercent());
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private MarksDtos.Sheet buildSheet(Subject subject) {
        List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subject.getId());
        List<Student> roster = roster(subject);
        List<MarkEntry> entries = markEntryRepository.findDetailedBySubject(subject.getId());
        Map<String, MarkEntry> entryByKey = entries.stream()
                .collect(Collectors.toMap(e -> key(e.getStudent().getId(), e.getComponent().getId()), Function.identity()));
        ModerationState state = moderationStateRepository.findBySubjectId(subject.getId()).orElse(null);
        var snapshot = metricsService.snapshot();

        List<MarksDtos.Row> rows = new ArrayList<>();
        int totalEntered = 0;
        double totalScoreSum = 0;
        int belowAttendance = 0;
        for (Student student : roster) {
            Map<Long, BigDecimal> scores = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;
            for (MarksComponent component : components) {
                MarkEntry entry = entryByKey.get(key(student.getId(), component.getId()));
                if (entry != null) {
                    scores.put(component.getId(), entry.getScore());
                    total = total.add(entry.getScore());
                    totalEntered++;
                }
            }
            Double attendance = snapshot.get(student.getId()) == null
                    ? null : snapshot.get(student.getId()).attendancePercent();
            if (attendance != null && attendance < threshold) {
                belowAttendance++;
            }
            totalScoreSum += total.doubleValue();
            rows.add(new MarksDtos.Row(student.getId(), student.getRollNo(), student.getName(), attendance,
                    scores, total));
        }
        int expected = roster.size() * components.size();
        double completeness = expected == 0 ? 0 : totalEntered * 100.0 / expected;
        var stateView = new MarksDtos.StateView(
                state == null ? "DRAFT" : state.getStatus().name(),
                state == null ? null : state.getLockedBy(),
                state == null || state.getLockedAt() == null ? null : state.getLockedAt().toString(),
                state == null ? null : state.getNote(),
                state == null ? null : state.getUnlockedBy(),
                state == null || state.getUnlockedAt() == null ? null : state.getUnlockedAt().toString(),
                state == null ? null : state.getUnlockReason());
        List<MarksDtos.AuditView> audit = auditService.forEntity("SUBJECT_MARKS", subject.getId().toString()).stream()
                .map(this::auditView).toList();
        boolean isFacultyAssigned = !accessService.user().isAdmin() && accessService.canTeachSubject(subject.getId());
        boolean canEdit = isFacultyAssigned && (state == null || state.getStatus() == ModerationState.Status.DRAFT);
        boolean canModerate = accessService.user().isAdmin();

        return new MarksDtos.Sheet(
                new MarksDtos.SubjectInfo(subject.getId(), subject.getCode(), subject.getName(),
                        subject.getProgramme().getCode(), subject.getProgramme().getName(), subject.getStudyYear(),
                        subject.getCredits()),
                components.stream().map(c -> new MarksDtos.ComponentView(c.getId(), c.getName(), c.getMaxScore(),
                        c.getSortOrder())).toList(),
                stateView, rows,
                new MarksDtos.Stats(roster.size(), totalEntered, expected,
                        roster.isEmpty() ? 0 : Math.round(totalScoreSum / roster.size() * 10.0) / 10.0,
                        Math.round(completeness * 10.0) / 10.0, belowAttendance),
                canEdit, canModerate, audit, threshold);
    }

    private MarksDtos.AuditView auditView(AuditEvent event) {
        return new MarksDtos.AuditView(event.getId(), event.getActorName(), event.getAction(), event.getDetail(),
                event.getCreatedAt().toString());
    }

    private ModerationState moderationState(Subject subject) {
        return moderationStateRepository.findBySubjectId(subject.getId())
                .orElseGet(() -> moderationStateRepository.save(new ModerationState(subject)));
    }

    private List<Student> roster(Subject subject) {
        return studentRepository.findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(
                subject.getProgramme().getId(), subject.getStudyYear());
    }

    private String key(Long studentId, Long componentId) {
        return studentId + ":" + componentId;
    }

    private String csvText(String text) {
        if (text == null) {
            return "";
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
