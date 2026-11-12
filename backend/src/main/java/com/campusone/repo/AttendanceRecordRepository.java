package com.campusone.repo;

import com.campusone.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findBySessionId(Long sessionId);

    @Query("""
        select r from AttendanceRecord r
        join fetch r.session s join fetch s.subject subj
        where s.status = 'SUBMITTED'
        """)
    List<AttendanceRecord> findAllSubmitted();

    @Query("""
        select r from AttendanceRecord r
        join fetch r.session s join fetch s.subject subj
        where s.status = 'SUBMITTED' and s.date >= :from and s.date <= :to
        """)
    List<AttendanceRecord> findSubmittedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        select r from AttendanceRecord r
        join fetch r.session s join fetch s.subject subj
        where r.student.id = :studentId and s.status = 'SUBMITTED'
        order by s.date desc, s.period desc
        """)
    List<AttendanceRecord> findSubmittedForStudent(@Param("studentId") Long studentId);

    @Query("""
        select r from AttendanceRecord r
        join fetch r.session s
        where r.student.id = :studentId and s.status = 'SUBMITTED'
        order by s.date desc, s.period desc
        """)
    List<AttendanceRecord> findRecentForStudent(@Param("studentId") Long studentId);

    @Query("""
        select r from AttendanceRecord r
        join fetch r.session s
        where r.student.id = :studentId and s.subject.id = :subjectId and s.status = 'SUBMITTED'
        """)
    List<AttendanceRecord> findSubmittedForStudentAndSubject(@Param("studentId") Long studentId,
                                                             @Param("subjectId") Long subjectId);
}
