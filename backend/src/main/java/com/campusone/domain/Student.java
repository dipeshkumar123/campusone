package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
public class Student {
    public enum Category { GENERAL, OBC, SC, ST, EWS }
    public enum ConsentStatus { CONSENTED, PENDING, NOT_REQUIRED }
    public enum Gender { FEMALE, MALE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String rollNo;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id")
    private Programme programme;

    @Column(nullable = false)
    private int studyYear;

    @Column(nullable = false, length = 10)
    private String section;

    @Column(nullable = false, length = 20)
    private String batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(length = 160)
    private String email;

    @Column(length = 120)
    private String guardianName;

    @Column(length = 20)
    private String guardianPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsentStatus consentStatus;

    @Column(nullable = false)
    private LocalDate admittedOn;

    @Column(nullable = false)
    private boolean active = true;

    public Student(String rollNo, String name, Programme programme, int studyYear, String section, String batch,
                   Category category, Gender gender, String email, String guardianName, String guardianPhone,
                   ConsentStatus consentStatus, LocalDate admittedOn) {
        this.rollNo = rollNo;
        this.name = name;
        this.programme = programme;
        this.studyYear = studyYear;
        this.section = section;
        this.batch = batch;
        this.category = category;
        this.gender = gender;
        this.email = email;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.consentStatus = consentStatus;
        this.admittedOn = admittedOn;
    }
}
