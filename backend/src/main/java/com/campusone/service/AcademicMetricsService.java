package com.campusone.service;

import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.MarkEntry;
import com.campusone.domain.Student;
import com.campusone.repo.AttendanceRecordRepository;
import com.campusone.repo.MarkEntryRepository;
import com.campusone.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AcademicMetricsService {
    public record AttendanceStats(int present, int held, Double percent) {
        public boolean hasData() {
            return held > 0;
        }
    }

    public record MarkStats(double scored, double maxTotal, int entryCount, int subjectCount) {
        public Double percent() {
            return maxTotal <= 0 ? null : (scored / maxTotal) * 100.0;
        }

        public Double outOf40() {
            return maxTotal <= 0 ? null : (scored / maxTotal) * 40.0;
        }
    }

    public record StudentMetrics(Student student, AttendanceStats termAttendance, AttendanceStats monthAttendance,
                                 MarkStats marks, double recentTrendDelta) {
        public Double attendancePercent() {
            return termAttendance.percent();
        }

        public Double marksPercent() {
            return marks.percent();
        }
    }

    public record Snapshot(List<StudentMetrics> students, Map<Long, StudentMetrics> byStudentId) {
        public StudentMetrics get(Long studentId) {
            return byStudentId.get(studentId);
        }
    }

    private final StudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final MarkEntryRepository markEntryRepository;
    private final Clock clock;

    public AcademicMetricsService(StudentRepository studentRepository,
                                  AttendanceRecordRepository attendanceRecordRepository,
                                  MarkEntryRepository markEntryRepository,
                                  Clock clock) {
        this.studentRepository = studentRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.markEntryRepository = markEntryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        List<Student> students = studentRepository.findByActiveTrueOrderByName();
        List<AttendanceRecord> records = attendanceRecordRepository.findAllSubmitted();
        List<MarkEntry> marks = markEntryRepository.findAllDetailed();

        Map<Long, List<AttendanceRecord>> attendanceByStudent = new HashMap<>();
        for (AttendanceRecord record : records) {
            attendanceByStudent.computeIfAbsent(record.getStudent().getId(), k -> new ArrayList<>()).add(record);
        }
        Map<Long, List<MarkEntry>> marksByStudent = new HashMap<>();
        for (MarkEntry entry : marks) {
            marksByStudent.computeIfAbsent(entry.getStudent().getId(), k -> new ArrayList<>()).add(entry);
        }

        YearMonth month = YearMonth.now(clock);
        List<StudentMetrics> metrics = new ArrayList<>();
        for (Student student : students) {
            List<AttendanceRecord> studentRecords = attendanceByStudent.getOrDefault(student.getId(), List.of());
            AttendanceStats term = stats(studentRecords);
            List<AttendanceRecord> monthRecords = studentRecords.stream()
                    .filter(r -> !r.getSession().getDate().isBefore(month.atDay(1)))
                    .toList();
            AttendanceStats monthStats = stats(monthRecords);
            MarkStats markStats = markStats(marksByStudent.getOrDefault(student.getId(), List.of()));
            metrics.add(new StudentMetrics(student, term, monthStats, markStats, trend(studentRecords)));
        }
        Map<Long, StudentMetrics> byId = new HashMap<>();
        metrics.forEach(m -> byId.put(m.student().getId(), m));
        return new Snapshot(metrics, byId);
    }

    public static AttendanceStats stats(List<AttendanceRecord> records) {
        if (records.isEmpty()) {
            return new AttendanceStats(0, 0, null);
        }
        int present = (int) records.stream()
                .filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT)
                .count();
        double pct = present * 100.0 / records.size();
        return new AttendanceStats(present, records.size(), round1(pct));
    }

    public static MarkStats markStats(List<MarkEntry> entries) {
        double scored = entries.stream().mapToDouble(e -> e.getScore().doubleValue()).sum();
        double max = entries.stream().mapToDouble(e -> e.getComponent().getMaxScore()).sum();
        int subjects = (int) entries.stream().map(e -> e.getComponent().getSubject().getId()).distinct().count();
        return new MarkStats(scored, max, entries.size(), subjects);
    }

    public static double trend(List<AttendanceRecord> records) {
        if (records.size() < 5) {
            return 0.0;
        }
        List<AttendanceRecord> ordered = records.stream()
                .sorted(Comparator.comparing((AttendanceRecord r) -> r.getSession().getDate())
                        .thenComparingInt(r -> r.getSession().getPeriod()).reversed())
                .toList();
        List<AttendanceRecord> recent = ordered.subList(0, Math.min(4, ordered.size()));
        int earlierEnd = Math.min(8, ordered.size());
        if (earlierEnd <= recent.size()) {
            return 0.0;
        }
        List<AttendanceRecord> earlier = ordered.subList(recent.size(), earlierEnd);
        double recentRate = presentRate(recent);
        double earlierRate = presentRate(earlier);
        return round1(recentRate - earlierRate);
    }

    private static double presentRate(List<AttendanceRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }
        long present = records.stream().filter(r -> r.getStatus() != AttendanceRecord.Status.ABSENT).count();
        return present * 100.0 / records.size();
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
