package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mark_entries",
       uniqueConstraints = @UniqueConstraint(name = "uk_mark_entry", columnNames = {"student_id", "component_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MarkEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id")
    private MarksComponent component;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(length = 120)
    private String updatedBy;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public MarkEntry(Student student, MarksComponent component, BigDecimal score, String updatedBy) {
        this.student = student;
        this.component = component;
        this.score = score;
        this.updatedBy = updatedBy;
    }
}
