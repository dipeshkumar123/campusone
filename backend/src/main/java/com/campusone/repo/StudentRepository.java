package com.campusone.repo;

import com.campusone.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {
    Optional<Student> findByRollNoIgnoreCase(String rollNo);
    List<Student> findByActiveTrueOrderByName();
    List<Student> findByProgrammeIdInOrderByName(Collection<Long> programmeIds);
    List<Student> findByProgrammeIdOrderByName(Long programmeId);
    List<Student> findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(Long programmeId, int studyYear);

    @Query("select distinct s.studyYear from Student s where s.active = true order by s.studyYear")
    List<Integer> findDistinctStudyYears();
}
