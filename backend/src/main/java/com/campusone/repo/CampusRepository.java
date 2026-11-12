package com.campusone.repo;

import com.campusone.domain.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {
}
