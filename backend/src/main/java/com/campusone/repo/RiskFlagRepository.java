package com.campusone.repo;

import com.campusone.domain.RiskFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RiskFlagRepository extends JpaRepository<RiskFlag, Long> {
    Optional<RiskFlag> findByStudentId(Long studentId);
    List<RiskFlag> findByStatus(RiskFlag.Status status);
    List<RiskFlag> findAllByOrderByScoreDesc();

    @Query("""
        select f from RiskFlag f join fetch f.student s join fetch s.programme
        where f.student.programme.id in :programmeIds
        order by f.score desc
        """)
    List<RiskFlag> findForProgrammes(@Param("programmeIds") Collection<Long> programmeIds);

    @Query("""
        select f from RiskFlag f join fetch f.student s join fetch s.programme
        where f.student.programme.id in :programmeIds and f.status = :status
        order by f.score desc
        """)
    List<RiskFlag> findForProgrammesAndStatus(@Param("programmeIds") Collection<Long> programmeIds,
                                              @Param("status") RiskFlag.Status status);
}
