package com.campusone.repo;

import com.campusone.domain.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findBySubjectIdAndDateAndPeriod(Long subjectId, LocalDate date, int period);
    Optional<AttendanceSession> findByIdempotencyKey(String idempotencyKey);
    List<AttendanceSession> findByDate(LocalDate date);
    List<AttendanceSession> findBySubjectId(Long subjectId);

    @Query("select s from AttendanceSession s join fetch s.subject subj join fetch s.faculty f where s.date = :date")
    List<AttendanceSession> findDetailedByDate(@Param("date") LocalDate date);

    @Query("select s from AttendanceSession s join fetch s.subject subj join fetch s.faculty f order by s.date desc, s.period desc")
    List<AttendanceSession> findAllDetailed();

    @Query("select count(s) from AttendanceSession s where s.date = :date")
    long countByDate(@Param("date") LocalDate date);
}
