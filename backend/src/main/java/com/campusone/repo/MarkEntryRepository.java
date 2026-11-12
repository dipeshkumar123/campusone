package com.campusone.repo;

import com.campusone.domain.MarkEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MarkEntryRepository extends JpaRepository<MarkEntry, Long> {
    @Query("""
        select e from MarkEntry e
        join fetch e.student s join fetch e.component c join fetch c.subject
        where c.subject.id = :subjectId
        """)
    List<MarkEntry> findDetailedBySubject(@Param("subjectId") Long subjectId);

    @Query("""
        select e from MarkEntry e
        join fetch e.student s join fetch e.component c join fetch c.subject
        """)
    List<MarkEntry> findAllDetailed();

    @Query("""
        select e from MarkEntry e
        join fetch e.component c join fetch c.subject
        where e.student.id = :studentId
        """)
    List<MarkEntry> findDetailedByStudent(@Param("studentId") Long studentId);
}
