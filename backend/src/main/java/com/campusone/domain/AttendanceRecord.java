package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attendance_records",
       uniqueConstraints = @UniqueConstraint(name = "uk_attendance_record", columnNames = {"session_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecord {
    public enum Status { PRESENT, ABSENT, LATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    public AttendanceRecord(AttendanceSession session, Student student, Status status) {
        this.session = session;
        this.student = student;
        this.status = status;
    }
}
