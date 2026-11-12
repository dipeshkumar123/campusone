package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_sessions",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_attendance_class", columnNames = {"subject_id", "session_date", "period"}),
           @UniqueConstraint(name = "uk_attendance_idempotency", columnNames = "idempotency_key")
       })
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSession {
    public enum Status { SUBMITTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id")
    private User faculty;

    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SUBMITTED;

    @Column(length = 80)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant submittedAt = Instant.now();

    public AttendanceSession(Subject subject, User faculty, LocalDate date, int period, String idempotencyKey) {
        this.subject = subject;
        this.faculty = faculty;
        this.date = date;
        this.period = period;
        this.idempotencyKey = idempotencyKey;
    }
}
