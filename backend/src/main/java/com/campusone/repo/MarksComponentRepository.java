package com.campusone.repo;

import com.campusone.domain.MarksComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarksComponentRepository extends JpaRepository<MarksComponent, Long> {
    List<MarksComponent> findBySubjectIdOrderBySortOrder(Long subjectId);
}
