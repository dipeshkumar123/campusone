package com.campusone.repo;

import com.campusone.domain.Programme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgrammeRepository extends JpaRepository<Programme, Long> {
    List<Programme> findByCampusIdOrderByName(Long campusId);
}
