package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "risk_flags", uniqueConstraints = @UniqueConstraint(columnNames = "student_id"))
@Getter
@Setter
@NoArgsConstructor
public class RiskFlag {
    public enum Band { HIGH, MEDIUM, LOW }
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Band band;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false, length = 1000)
    private String reasons;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal attendancePercent;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal marksPercent;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal trendDelta;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant computedAt = Instant.now();

    public RiskFlag(Student student, int score, Band band, String reasons, BigDecimal attendancePercent,
                    BigDecimal marksPercent, BigDecimal trendDelta) {
        this.student = student;
        this.score = score;
        this.band = band;
        this.reasons = reasons;
        this.attendancePercent = attendancePercent;
        this.marksPercent = marksPercent;
        this.trendDelta = trendDelta;
    }
}
