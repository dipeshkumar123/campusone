package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(columnNames = {"code", "programme_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 140)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id")
    private Programme programme;

    @Column(nullable = false)
    private int studyYear;

    @Column(nullable = false)
    private int credits;

    public Subject(String code, String name, Programme programme, int studyYear, int credits) {
        this.code = code;
        this.name = name;
        this.programme = programme;
        this.studyYear = studyYear;
        this.credits = credits;
    }
}
