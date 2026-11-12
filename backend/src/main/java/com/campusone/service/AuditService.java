package com.campusone.service;

import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.AuditEvent;
import com.campusone.repo.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void log(String action, String entityType, String entityId, String detail) {
        String actorName = "System";
        Long actorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            actorName = user.name();
            actorId = user.id();
        }
        auditEventRepository.save(new AuditEvent(actorName, actorId, action, entityType, entityId, detail));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forEntity(String entityType, String entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recent() {
        return auditEventRepository.findAllByOrderByCreatedAtDesc().stream().limit(100).toList();
    }
}
