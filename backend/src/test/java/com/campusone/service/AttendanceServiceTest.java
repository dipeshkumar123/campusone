package com.campusone.service;

import com.campusone.api.dto.AttendanceDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.repo.AttendanceSessionRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.UserRepository;
import com.campusone.support.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceServiceTest {
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private AcademicCalendar calendar;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private AttendanceSessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;

    private Subject subject;
    private List<Student> roster;

    @BeforeEach
    void setUp() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("faculty@campusone.demo").orElseThrow());
        subject = subjectRepository.findAll().stream()
                .filter(s -> s.getCode().equals("CSE301"))
                .findFirst().orElseThrow();
        roster = studentRepository.findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(
                subject.getProgramme().getId(), subject.getStudyYear());
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void syncIsIdempotentForTheSameDeviceKey() {
        List<AttendanceDtos.SyncRecord> records = records(AttendanceRecord.Status.PRESENT);
        records.set(0, new AttendanceDtos.SyncRecord(roster.get(0).getId(), AttendanceRecord.Status.ABSENT));
        String clientId = UUID.randomUUID().toString();
        LocalDate date = calendar.today();

        AttendanceDtos.SyncResponse first = attendanceService.sync(
                new AttendanceDtos.SyncRequest(subject.getId(), date, 1, clientId, records));

        assertThat(first.duplicate()).isFalse();
        assertThat(first.total()).isEqualTo(roster.size());
        assertThat(first.presentCount()).isEqualTo(roster.size() - 1);
        Long sessionId = first.sessionId();

        List<AttendanceDtos.SyncRecord> changed = records(AttendanceRecord.Status.ABSENT);
        AttendanceDtos.SyncResponse second = attendanceService.sync(
                new AttendanceDtos.SyncRequest(subject.getId(), date, 1, clientId, changed));

        assertThat(second.duplicate()).isTrue();
        assertThat(second.sessionId()).isEqualTo(sessionId);
        assertThat(second.presentCount()).isEqualTo(roster.size() - 1);
        assertThat(second.message()).contains("Already synced");
        assertThat(sessionRepository.findBySubjectIdAndDateAndPeriod(subject.getId(), date, 1))
                .isPresent();
    }

    @Test
    void partialRosterIsRejectedSoAttendanceCannotSilentlyLoseStudents() {
        List<AttendanceDtos.SyncRecord> records = records(AttendanceRecord.Status.PRESENT);
        records.remove(records.size() - 1);

        assertThatThrownBy(() -> attendanceService.sync(new AttendanceDtos.SyncRequest(
                subject.getId(), calendar.today(), 2, UUID.randomUUID().toString(), records)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("every student");
    }

    @Test
    void duplicateStudentInSheetIsRejected() {
        List<AttendanceDtos.SyncRecord> records = records(AttendanceRecord.Status.PRESENT);
        records.set(records.size() - 1,
                new AttendanceDtos.SyncRecord(records.get(0).studentId(), AttendanceRecord.Status.PRESENT));

        assertThatThrownBy(() -> attendanceService.sync(new AttendanceDtos.SyncRequest(
                subject.getId(), calendar.today(), 2, UUID.randomUUID().toString(), records)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("same student");
    }

    @Test
    void futureAndStaleClassesAreRejected() {
        List<AttendanceDtos.SyncRecord> records = records(AttendanceRecord.Status.PRESENT);

        assertThatThrownBy(() -> attendanceService.sync(new AttendanceDtos.SyncRequest(
                subject.getId(), calendar.today().plusDays(1), 2, UUID.randomUUID().toString(), records)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("future");

        assertThatThrownBy(() -> attendanceService.sync(new AttendanceDtos.SyncRequest(
                subject.getId(), calendar.today().minusDays(15), 2, UUID.randomUUID().toString(), records)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("14 days old");
    }

    @Test
    void officeAdminCannotSubmitAttendance() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("admin@campusone.demo").orElseThrow());

        assertThatThrownBy(() -> attendanceService.sync(new AttendanceDtos.SyncRequest(
                subject.getId(), calendar.today(), 2, UUID.randomUUID().toString(),
                records(AttendanceRecord.Status.PRESENT))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Teachers enter attendance");
    }

    private List<AttendanceDtos.SyncRecord> records(AttendanceRecord.Status status) {
        List<AttendanceDtos.SyncRecord> records = new ArrayList<>();
        roster.forEach(student -> records.add(new AttendanceDtos.SyncRecord(student.getId(), status)));
        return records;
    }
}
