package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "programmes")
@Getter
@Setter
@NoArgsConstructor
public class Programme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campus_id")
    private Campus campus;

    @Column(nullable = false)
    private int durationYears;

    public Programme(String code, String name, String department, Campus campus, int durationYears) {
        this.code = code;
        this.name = name;
        this.department = department;
        this.campus = campus;
        this.durationYears = durationYears;
    }
}
