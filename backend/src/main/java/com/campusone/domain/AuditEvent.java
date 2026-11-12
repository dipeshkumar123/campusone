package com.campusone.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String actorName;

    private Long actorId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 40)
    private String entityType;

    @Column(length = 40)
    private String entityId;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditEvent(String actorName, Long actorId, String action, String entityType, String entityId, String detail) {
        this.actorName = actorName;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
    }
}
