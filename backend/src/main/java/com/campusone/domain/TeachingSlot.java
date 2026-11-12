package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;

@Entity
@Table(name = "teaching_slots")
@Getter
@Setter
@NoArgsConstructor
public class TeachingSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id")
    private User faculty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 30)
    private String room;

    public TeachingSlot(Subject subject, User faculty, DayOfWeek dayOfWeek, int period, String room) {
        this.subject = subject;
        this.faculty = faculty;
        this.dayOfWeek = dayOfWeek;
        this.period = period;
        this.room = room;
    }
}
