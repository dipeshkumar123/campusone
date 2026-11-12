package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fee_accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_year"}))
@Getter
@Setter
@NoArgsConstructor
public class FeeAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(nullable = false, length = 20)
    private String academicYear;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal scholarshipAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate dueDate;

    private Instant lastReminderAt;

    public FeeAccount(Student student, String academicYear, BigDecimal grossAmount, BigDecimal scholarshipAmount,
                      BigDecimal paidAmount, BigDecimal dueAmount, LocalDate dueDate) {
        this.student = student;
        this.academicYear = academicYear;
        this.grossAmount = grossAmount;
        this.scholarshipAmount = scholarshipAmount;
        this.paidAmount = paidAmount;
        this.dueAmount = dueAmount;
        this.dueDate = dueDate;
    }
}
