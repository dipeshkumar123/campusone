package com.campusone.service;

import com.campusone.api.dto.FeeDtos;
import com.campusone.api.dto.StudentDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.MarkEntry;
import com.campusone.domain.MarksComponent;
import com.campusone.domain.MentorTask;
import com.campusone.domain.ModerationState;
import com.campusone.domain.RiskFlag;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.repo.AttendanceRecordRepository;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.MarkEntryRepository;
import com.campusone.repo.MarksComponentRepository;
import com.campusone.repo.MentorTaskRepository;
import com.campusone.repo.ModerationStateRepository;
import com.campusone.repo.RiskFlagRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
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
public class StudentService {
    private final AccessService accessService;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final MarksComponentRepository componentRepository;
    private final MarkEntryRepository markEntryRepository;
    private final ModerationStateRepository moderationStateRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AcademicMetricsService metricsService;
    private final FeeAccountRepository feeAccountRepository;
    private final RiskFlagRepository riskFlagRepository;
    private final MentorTaskRepository mentorTaskRepository;
    private final FeeService feeService;
    private final AcademicCalendar calendar;
    private final int threshold;

    public StudentService(AccessService accessService, StudentRepository studentRepository,
                          SubjectRepository subjectRepository, MarksComponentRepository componentRepository,
                          MarkEntryRepository markEntryRepository, ModerationStateRepository moderationStateRepository,
                          AttendanceRecordRepository attendanceRecordRepository,
                          AcademicMetricsService metricsService, FeeAccountRepository feeAccountRepository,
                          RiskFlagRepository riskFlagRepository, MentorTaskRepository mentorTaskRepository,
                          FeeService feeService, AcademicCalendar calendar,
                          @Value("${app.attendance.threshold-percent:75}") int threshold) {
        this.accessService = accessService;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.componentRepository = componentRepository;
        this.markEntryRepository = markEntryRepository;
        this.moderationStateRepository = moderationStateRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.metricsService = metricsService;
        this.feeAccountRepository = feeAccountRepository;
        this.riskFlagRepository = riskFlagRepository;
        this.mentorTaskRepository = mentorTaskRepository;
        this.feeService = feeService;
        this.calendar = calendar;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public StudentDtos.ListView list(String query, Long programmeId, Integer year) {
        var user = accessService.user();
        List<Student> students = accessService.visibleStudents();
        var snapshot = metricsService.snapshot();
        Map<Long, RiskFlag> risks = new HashMap<>();
        riskFlagRepository.findAll().stream()
                .filter(f -> f.getStatus() != RiskFlag.Status.RESOLVED)
                .forEach(f -> risks.put(f.getStudent().getId(), f));
        Map<Long, FeeAccount> fees = new HashMap<>();
        if (user.isAdmin()) {
            feeAccountRepository.findAll().forEach(a -> fees.put(a.getStudent().getId(), a));
        }

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<StudentDtos.Row> rows = students.stream()
                .filter(s -> q.isBlank() || s.getName().toLowerCase(Locale.ROOT).contains(q)
                        || s.getRollNo().toLowerCase(Locale.ROOT).contains(q))
                .filter(s -> programmeId == null || s.getProgramme().getId().equals(programmeId))
                .filter(s -> year == null || s.getStudyYear() == year)
                .sorted(Comparator.comparing(Student::getName))
                .map(s -> {
                    var metric = snapshot.get(s.getId());
                    RiskFlag flag = risks.get(s.getId());
                    FeeAccount fee = fees.get(s.getId());
                    return new StudentDtos.Row(s.getId(), s.getRollNo(), s.getName(),
                            s.getProgramme().getCode(), s.getProgramme().getName(), s.getStudyYear(),
                            s.getSection(), s.getCategory().name(),
                            metric == null ? null : metric.attendancePercent(),
                            metric == null || metric.marks().outOf40() == null ? null
                                    : round1(metric.marks().outOf40()),
                            fee == null ? null : fee.getDueAmount(),
                            flag == null ? null : flag.getBand().name(),
                            flag == null ? null : flag.getStatus().name(),
                            s.getConsentStatus().name());
                }).toList();
        return new StudentDtos.ListView(rows, rows.size());
    }

    @Transactional(readOnly = true)
    public StudentDtos.Profile profile(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student not found."));
        accessService.assertCanView(student);
        var user = accessService.user();
        var metric = metricsService.snapshot().get(studentId);

        List<Subject> subjects = subjectRepository.findByProgrammeIdAndStudyYearOrderByCode(
                student.getProgramme().getId(), student.getStudyYear());
        List<AttendanceRecord> attendance = attendanceRecordRepository.findSubmittedForStudent(studentId);
        Map<Long, List<AttendanceRecord>> attendanceBySubject = attendance.stream()
                .collect(Collectors.groupingBy(r -> r.getSession().getSubject().getId()));
        List<StudentDtos.SubjectAttendance> attendanceRows = new ArrayList<>();
        for (Subject subject : subjects) {
            var stats = AcademicMetricsService.stats(attendanceBySubject.getOrDefault(subject.getId(), List.of()));
            attendanceRows.add(new StudentDtos.SubjectAttendance(subject.getId(), subject.getCode(), subject.getName(),
                    stats.present(), stats.held(), stats.percent(),
                    stats.percent() != null && stats.percent() < threshold));
        }

        List<MarkEntry> markEntries = markEntryRepository.findDetailedByStudent(studentId);
        Map<Long, Map<Long, BigDecimal>> scoresBySubjectComponent = new HashMap<>();
        for (MarkEntry entry : markEntries) {
            scoresBySubjectComponent
                    .computeIfAbsent(entry.getComponent().getSubject().getId(), k -> new HashMap<>())
                    .put(entry.getComponent().getId(), entry.getScore());
        }
        List<StudentDtos.SubjectMarks> marksRows = new ArrayList<>();
        for (Subject subject : subjects) {
            List<MarksComponent> components = componentRepository.findBySubjectIdOrderBySortOrder(subject.getId());
            if (components.isEmpty()) {
                continue;
            }
            Map<Long, BigDecimal> scores = scoresBySubjectComponent.getOrDefault(subject.getId(), Map.of());
            List<StudentDtos.ComponentScore> componentScores = components.stream()
                    .map(c -> new StudentDtos.ComponentScore(c.getId(), c.getName(), c.getMaxScore(),
                            scores.get(c.getId())))
                    .toList();
            BigDecimal total = componentScores.stream().map(StudentDtos.ComponentScore::score)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            ModerationState state = moderationStateRepository.findBySubjectId(subject.getId()).orElse(null);
            marksRows.add(new StudentDtos.SubjectMarks(subject.getId(), subject.getCode(), subject.getName(),
                    state == null ? "DRAFT" : state.getStatus().name(), componentScores, total,
                    components.stream().mapToInt(MarksComponent::getMaxScore).sum()));
        }

        RiskFlag flag = riskFlagRepository.findByStudentId(studentId).orElse(null);
        StudentDtos.RiskSnapshot risk = null;
        if (flag != null) {
            MentorTask latestTask = mentorTaskRepository.findByRiskFlagIdOrderByCreatedAtDesc(flag.getId())
                    .stream().findFirst().orElse(null);
            StudentDtos.MentorTaskSummary taskSummary = latestTask == null ? null
                    : new StudentDtos.MentorTaskSummary(latestTask.getId(), latestTask.getAssignee().getName(),
                    latestTask.getDueDate().toString(), latestTask.getStatus().name(), latestTask.getNote());
            risk = new StudentDtos.RiskSnapshot(flag.getId(), flag.getScore(), flag.getBand().name(),
                    flag.getStatus().name(), Arrays.stream(flag.getReasons().split("\n")).filter(s -> !s.isBlank()).toList(),
                    flag.getAttendancePercent(), flag.getMarksPercent(), flag.getTrendDelta(), taskSummary);
        }

        FeeDtos.FeeDetail fee = null;
        if (user.isAdmin() && feeAccountRepository.findByStudentId(studentId).isPresent()) {
            fee = feeService.detail(studentId);
        }

        return new StudentDtos.Profile(student.getId(), student.getRollNo(), student.getName(), student.getEmail(),
                student.getProgramme().getCode(), student.getProgramme().getName(),
                student.getProgramme().getDepartment(), student.getStudyYear(), student.getSection(),
                student.getBatch(), student.getCategory().name(), student.getGender().name(),
                student.getConsentStatus().name(), student.getGuardianName(), student.getGuardianPhone(),
                student.getAdmittedOn().toString(), student.isActive() ? "Active" : "Inactive",
                metric == null ? null : metric.attendancePercent(),
                metric == null ? 0 : metric.termAttendance().present(),
                metric == null ? 0 : metric.termAttendance().held(),
                metric == null || metric.marks().outOf40() == null ? null : round1(metric.marks().outOf40()),
                metric == null || metric.marksPercent() == null ? null : round1(metric.marksPercent()),
                attendanceRows, marksRows, risk, fee, calendar.academicYear(), threshold);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
