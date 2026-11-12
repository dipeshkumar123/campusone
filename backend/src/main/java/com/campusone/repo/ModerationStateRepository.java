package com.campusone.repo;

import com.campusone.domain.ModerationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModerationStateRepository extends JpaRepository<ModerationState, Long> {
    Optional<ModerationState> findBySubjectId(Long subjectId);
    List<ModerationState> findByStatus(ModerationState.Status status);
}
