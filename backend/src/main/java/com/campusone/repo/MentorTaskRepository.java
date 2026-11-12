package com.campusone.repo;

import com.campusone.domain.MentorTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorTaskRepository extends JpaRepository<MentorTask, Long> {
    List<MentorTask> findByRiskFlagIdOrderByCreatedAtDesc(Long riskFlagId);
    List<MentorTask> findByStatusOrderByDueDateAsc(MentorTask.Status status);
    long countByStatus(MentorTask.Status status);
}
