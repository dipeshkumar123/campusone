package com.campusone.repo;

import com.campusone.domain.TeachingSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface TeachingSlotRepository extends JpaRepository<TeachingSlot, Long> {
    List<TeachingSlot> findByFacultyIdOrderByPeriod(Long facultyId);
    List<TeachingSlot> findByFacultyIdAndDayOfWeekOrderByPeriod(Long facultyId, DayOfWeek dayOfWeek);
    boolean existsByFacultyIdAndSubjectId(Long facultyId, Long subjectId);
    List<TeachingSlot> findBySubjectId(Long subjectId);
}
