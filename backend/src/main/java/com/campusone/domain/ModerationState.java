package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "moderation_states", uniqueConstraints = @UniqueConstraint(columnNames = "subject_id"))
@Getter
@Setter
@NoArgsConstructor
public class ModerationState {
    public enum Status { DRAFT, LOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 120)
    private String lockedBy;

    private Instant lockedAt;

    @Column(length = 500)
    private String note;

    @Column(length = 120)
    private String unlockedBy;

    private Instant unlockedAt;

    @Column(length = 500)
    private String unlockReason;

    public ModerationState(Subject subject) {
        this.subject = subject;
    }
}
