package com.campusone.service;

import com.campusone.api.dto.MarksDtos;
import com.campusone.common.ApiException;
import com.campusone.domain.Subject;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.UserRepository;
import com.campusone.support.TestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarksServiceTest {
    @Autowired
    private MarksService marksService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private UserRepository userRepository;

    private Subject ds;
    private Subject dbms;

    @BeforeEach
    void setUp() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("faculty@campusone.demo").orElseThrow());
        ds = subjectByCode("CSE301");
        dbms = subjectByCode("CSE302");
    }

    @AfterEach
    void tearDown() {
        TestSecurity.clear();
    }

    @Test
    void facultyCannotEditLockedMarks() {
        MarksDtos.Sheet locked = marksService.sheet(dbms.getId());
        assertThat(locked.state().status()).isEqualTo("LOCKED");

        MarksDtos.SaveEntry entry = new MarksDtos.SaveEntry(
                locked.rows().get(0).studentId(),
                locked.components().get(0).id(),
                BigDecimal.ONE
        );

        assertThatThrownBy(() -> marksService.saveDraft(dbms.getId(),
                new MarksDtos.SaveRequest(List.of(entry))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("locked after moderation");
    }

    @Test
    void facultyCannotModerateALockedOrDraftSubject() {
        assertThatThrownBy(() -> marksService.lock(ds.getId(), new MarksDtos.LockRequest("Moderated")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("office admins");
    }

    @Test
    void adminCanLockAndUnlockWithAnAuditedReason() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("admin@campusone.demo").orElseThrow());

        MarksDtos.Sheet locked = marksService.lock(ds.getId(),
                new MarksDtos.LockRequest("Department moderation complete"));
        assertThat(locked.state().status()).isEqualTo("LOCKED");
        assertThat(locked.state().lockedBy()).isEqualTo("Sasmita Nayak");

        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("faculty@campusone.demo").orElseThrow());
        assertThatThrownBy(() -> marksService.saveDraft(ds.getId(), new MarksDtos.SaveRequest(List.of(
                new MarksDtos.SaveEntry(locked.rows().get(0).studentId(),
                        locked.components().get(0).id(), BigDecimal.ONE)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("locked");

        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("admin@campusone.demo").orElseThrow());
        MarksDtos.Sheet unlocked = marksService.unlock(ds.getId(),
                new MarksDtos.UnlockRequest("Correcting an office data-entry mistake"));
        assertThat(unlocked.state().status()).isEqualTo("DRAFT");
        assertThat(unlocked.state().unlockReason()).contains("data-entry");

        assertThat(auditService.forEntity("SUBJECT_MARKS", ds.getId().toString()))
                .extracting(event -> event.getAction())
                .contains("MODERATION_LOCKED", "MODERATION_UNLOCKED");
    }

    @Test
    void adminCanUnlockSeededDatabaseMarksWithReason() {
        TestSecurity.signInAs(userRepository.findByEmailIgnoreCase("admin@campusone.demo").orElseThrow());

        MarksDtos.Sheet unlocked = marksService.unlock(dbms.getId(),
                new MarksDtos.UnlockRequest("External moderation correction"));
        assertThat(unlocked.state().status()).isEqualTo("DRAFT");
        assertThat(unlocked.state().unlockedBy()).isEqualTo("Sasmita Nayak");
    }

    @Test
    void outOfRangeScoreIsRejected() {
        MarksDtos.Sheet sheet = marksService.sheet(ds.getId());
        MarksDtos.ComponentView component = sheet.components().get(0);

        assertThatThrownBy(() -> marksService.saveDraft(ds.getId(), new MarksDtos.SaveRequest(List.of(
                new MarksDtos.SaveEntry(sheet.rows().get(0).studentId(), component.id(),
                        BigDecimal.valueOf(component.maxScore() + 1))))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must be between 0 and " + component.maxScore());
    }

    private Subject subjectByCode(String code) {
        return subjectRepository.findAll().stream()
                .filter(s -> s.getCode().equals(code))
                .findFirst().orElseThrow();
    }
}
