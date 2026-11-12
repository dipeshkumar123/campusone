package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mentor_tasks")
@Getter
@Setter
@NoArgsConstructor
public class MentorTask {
    public enum Status { OPEN, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_flag_id")
    private RiskFlag riskFlag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false, length = 120)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public MentorTask(RiskFlag riskFlag, Student student, User assignee, String note, LocalDate dueDate,
                      String createdBy) {
        this.riskFlag = riskFlag;
        this.student = student;
        this.assignee = assignee;
        this.note = note;
        this.dueDate = dueDate;
        this.createdBy = createdBy;
    }
}
