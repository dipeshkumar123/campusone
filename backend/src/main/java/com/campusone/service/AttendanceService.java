package com.campusone.service;

import com.campusone.api.dto.AttendanceDtos;
import com.campusone.common.ApiException;
import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.AttendanceSession;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.domain.TeachingSlot;
import com.campusone.repo.AttendanceRecordRepository;
import com.campusone.repo.AttendanceSessionRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.TeachingSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    private final AccessService accessService;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final TeachingSlotRepository teachingSlotRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final AcademicMetricsService metricsService;
    private final AcademicCalendar calendar;
    private final AuditService auditService;
    private final int threshold;
    private final int maxBackfillDays;

    public AttendanceService(AccessService accessService, StudentRepository studentRepository,
                             SubjectRepository subjectRepository, TeachingSlotRepository teachingSlotRepository,
                             AttendanceSessionRepository sessionRepository, AttendanceRecordRepository recordRepository,
                             AcademicMetricsService metricsService, AcademicCalendar calendar, AuditService auditService,
                             @Value("${app.attendance.threshold-percent:75}") int threshold,
                             @Value("${app.attendance.max-backfill-days:14}") int maxBackfillDays) {
        this.accessService = accessService;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.teachingSlotRepository = teachingSlotRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.metricsService = metricsService;
        this.calendar = calendar;
        this.auditService = auditService;
        this.threshold = threshold;
        this.maxBackfillDays = maxBackfillDays;
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.MyClassesView myClasses(LocalDate requestedDate) {
        AuthenticatedUser user = accessService.user();
        if (user.isAdmin()) {
            throw ApiException.forbidden("Attendance is taken by faculty. Sign in with the faculty demo account.");
        }
        LocalDate date = requestedDate == null ? calendar.today() : requestedDate;
        List<TeachingSlot> allSlots = teachingSlotRepository.findByFacultyIdOrderByPeriod(user.id());
        List<TeachingSlot> daySlots = allSlots.stream()
                .filter(slot -> slot.getDayOfWeek() == date.getDayOfWeek())
                .sorted(Comparator.comparingInt(TeachingSlot::getPeriod))
                .toList();

        List<AttendanceDtos.ClassCard> cards = new ArrayList<>();
        for (TeachingSlot slot : daySlots) {
            Subject subject = slot.getSubject();
            var session = sessionRepository.findBySubjectIdAndDateAndPeriod(subject.getId(), date, slot.getPeriod());
            Integer present = null;
            Integer total = null;
            Double pct = null;
            if (session.isPresent()) {
                List<AttendanceRecord> records = recordRepository.findBySessionId(session.get().getId());
                present = (int) records.stream().filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT).count();
                total = records.size();
                pct = total == 0 ? null : AcademicMetricsService.round1(present * 100.0 / total);
            }
            cards.add(new AttendanceDtos.ClassCard(
                    subject.getId(), subject.getCode(), subject.getName(),
                    subject.getProgramme().getCode(), subject.getStudyYear(),
                    slot.getPeriod(), slot.getRoom(),
                    session.isPresent(), session.map(s -> s.getSubmittedAt().toString()).orElse(null),
                    present, total, pct));
        }

        Map<Long, AttendanceDtos.TeachingSubject> subjects = new LinkedHashMap<>();
        allSlots.stream().map(TeachingSlot::getSubject)
                .sorted(Comparator.comparing(Subject::getCode))
                .forEach(subject -> subjects.putIfAbsent(subject.getId(), new AttendanceDtos.TeachingSubject(
                        subject.getId(), subject.getCode(), subject.getName(), subject.getStudyYear(),
                        subject.getProgramme().getCode())));

        return new AttendanceDtos.MyClassesView(date.toString(), date.format(DATE_LABEL), cards,
                new ArrayList<>(subjects.values()), threshold);
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.RosterView roster(Long subjectId, LocalDate date, int period) {
        Subject subject = accessService.requireViewableSubject(subjectId);
        if (date == null) {
            throw ApiException.badRequest("Class date is required.");
        }
        if (date.isAfter(calendar.today())) {
            throw ApiException.badRequest("Attendance cannot be taken for a future class.");
        }
        if (period < 1 || period > 8) {
            throw ApiException.badRequest("Period must be between 1 and 8.");
        }

        List<Student> students = studentRepository
                .findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(subject.getProgramme().getId(), subject.getStudyYear());
        var sessionOpt = sessionRepository.findBySubjectIdAndDateAndPeriod(subjectId, date, period);
        Map<Long, AttendanceRecord> records = sessionOpt
                .map(session -> recordRepository.findBySessionId(session.getId()).stream()
                        .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity())))
                .orElse(Map.of());
        var snapshot = metricsService.snapshot();

        List<AttendanceDtos.RosterStudent> roster = students.stream()
                .map(student -> {
                    Double pct = snapshot.get(student.getId()) == null
                            ? null : snapshot.get(student.getId()).attendancePercent();
                    AttendanceRecord record = records.get(student.getId());
                    return new AttendanceDtos.RosterStudent(
                            student.getId(), student.getRollNo(), student.getName(),
                            pct, pct != null && pct < threshold,
                            record == null ? null : record.getStatus());
                }).toList();

        AttendanceDtos.SessionInfo sessionInfo = sessionOpt
                .map(session -> new AttendanceDtos.SessionInfo(
                        session.getId(), session.getSubmittedAt().toString(), session.getFaculty().getName()))
                .orElse(null);
        int present = (int) roster.stream().filter(r -> r.status() == AttendanceRecord.Status.PRESENT
                || r.status() == AttendanceRecord.Status.LATE).count();
        int absent = (int) roster.stream().filter(r -> r.status() == AttendanceRecord.Status.ABSENT).count();
        int late = (int) roster.stream().filter(r -> r.status() == AttendanceRecord.Status.LATE).count();
        int unmarked = (int) roster.stream().filter(r -> r.status() == null).count();

        boolean canEdit = !accessService.user().isAdmin()
                && accessService.canTeachSubject(subjectId)
                && !date.isBefore(calendar.today().minusDays(maxBackfillDays));

        return new AttendanceDtos.RosterView(
                subject.getId(), subject.getCode(), subject.getName(),
                subject.getProgramme().getCode(), subject.getProgramme().getName(), subject.getStudyYear(),
                date.toString(), period, threshold, sessionInfo, roster,
                new AttendanceDtos.Counts(present, absent, late, unmarked), canEdit);
    }

    @Transactional
    public AttendanceDtos.SyncResponse sync(AttendanceDtos.SyncRequest request) {
        Subject subject = accessService.requireTeachSubject(request.subjectId());
        LocalDate today = calendar.today();
        if (request.date().isAfter(today)) {
            throw ApiException.badRequest("Attendance cannot be taken for a future class.");
        }
        if (request.date().isBefore(today.minusDays(maxBackfillDays))) {
            throw ApiException.badRequest("This class is more than " + maxBackfillDays + " days old. Ask the office to correct it.");
        }

        List<Student> roster = studentRepository
                .findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(subject.getProgramme().getId(), subject.getStudyYear());
        Map<Long, Student> rosterById = roster.stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (AttendanceDtos.SyncRecord record : request.records()) {
            if (!seen.add(record.studentId())) {
                throw ApiException.badRequest("The same student appears twice in this attendance sheet.");
            }
            if (!rosterById.containsKey(record.studentId())) {
                throw ApiException.badRequest("A student in this sheet is not in the class roster.");
            }
        }
        if (seen.size() != roster.size()) {
            throw ApiException.badRequest("Save attendance for every student in the class so the record stays complete.");
        }

        AttendanceSession session = sessionRepository
                .findBySubjectIdAndDateAndPeriod(subject.getId(), request.date(), request.period())
                .orElse(null);
        if (session != null && request.clientId().equals(session.getIdempotencyKey())) {
            List<AttendanceRecord> existing = recordRepository.findBySessionId(session.getId());
            int present = (int) existing.stream().filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT).count();
            return new AttendanceDtos.SyncResponse(session.getId(), session.getDate().toString(), session.getPeriod(),
                    session.getSubmittedAt().toString(), true, present, existing.size() - present,
                    existing.size(), "Already synced from this device.");
        }

        Instant now = Instant.now();
        if (session == null) {
            session = new AttendanceSession(subject, accessService.userEntity(), request.date(), request.period(), request.clientId());
            session.setSubmittedAt(now);
            session = sessionRepository.save(session);
        } else {
            session.setIdempotencyKey(request.clientId());
            session.setSubmittedAt(now);
            session.setFaculty(accessService.userEntity());
            session = sessionRepository.save(session);
        }

        Map<Long, AttendanceRecord> existing = recordRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), Function.identity()));
        for (AttendanceDtos.SyncRecord entry : request.records()) {
            AttendanceRecord record = existing.get(entry.studentId());
            if (record == null) {
                record = new AttendanceRecord(session, rosterById.get(entry.studentId()), entry.status());
            } else {
                record.setStatus(entry.status());
            }
            recordRepository.save(record);
        }

        int present = (int) request.records().stream()
                .filter(r -> r.status() != AttendanceRecord.Status.ABSENT).count();
        auditService.log("ATTENDANCE_SUBMITTED", "ATTENDANCE_SESSION", session.getId().toString(),
                subject.getCode() + " · " + request.date() + " · period " + request.period()
                        + " · " + present + " present, " + (request.records().size() - present) + " absent");

        return new AttendanceDtos.SyncResponse(session.getId(), request.date().toString(), request.period(),
                session.getSubmittedAt().toString(), false, present, request.records().size() - present,
                request.records().size(), "Attendance saved.");
    }
}
