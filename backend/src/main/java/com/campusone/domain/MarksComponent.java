package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marks_components")
@Getter
@Setter
@NoArgsConstructor
public class MarksComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int maxScore;

    @Column(nullable = false)
    private int sortOrder;

    public MarksComponent(Subject subject, String name, int maxScore, int sortOrder) {
        this.subject = subject;
        this.name = name;
        this.maxScore = maxScore;
        this.sortOrder = sortOrder;
    }
}
