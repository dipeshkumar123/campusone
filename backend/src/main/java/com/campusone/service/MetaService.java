package com.campusone.service;

import com.campusone.api.dto.MetaContext;
import com.campusone.api.dto.UserView;
import com.campusone.config.AuthenticatedUser;
import com.campusone.domain.Programme;
import com.campusone.domain.Student;
import com.campusone.domain.TeachingSlot;
import com.campusone.domain.User;
import com.campusone.repo.ProgrammeRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.TeachingSlotRepository;
import com.campusone.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class MetaService {
    private final AccessService accessService;
    private final AcademicCalendar calendar;
    private final ProgrammeRepository programmeRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeachingSlotRepository teachingSlotRepository;
    private final int threshold;

    public MetaService(AccessService accessService, AcademicCalendar calendar, ProgrammeRepository programmeRepository,
                       StudentRepository studentRepository, UserRepository userRepository,
                       TeachingSlotRepository teachingSlotRepository,
                       @Value("${app.attendance.threshold-percent:75}") int threshold) {
        this.accessService = accessService;
        this.calendar = calendar;
        this.programmeRepository = programmeRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.teachingSlotRepository = teachingSlotRepository;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public MetaContext context() {
        AuthenticatedUser user = accessService.user();
        Set<Long> visibleIds = accessService.visibleProgrammeIds();
        List<Programme> visibleProgrammes = programmeRepository.findAll().stream()
                .filter(p -> visibleIds.contains(p.getId()))
                .sorted(java.util.Comparator.comparing(Programme::getName))
                .toList();
        List<Student> students = accessService.visibleStudents();
        List<Integer> years = new ArrayList<>(new TreeSet<>(students.stream().map(Student::getStudyYear).toList()));

        List<TeachingSlot> allSlots = teachingSlotRepository.findAll();
        Map<Long, List<TeachingSlot>> slotsByFaculty = new LinkedHashMap<>();
        allSlots.forEach(slot -> slotsByFaculty.computeIfAbsent(slot.getFaculty().getId(), k -> new ArrayList<>()).add(slot));

        List<MetaContext.FacultyOption> faculty = userRepository.findByRoleAndActiveTrueOrderByName(User.Role.FACULTY)
                .stream()
                .map(f -> new MetaContext.FacultyOption(f.getId(), f.getName(), f.getTitle(),
                        slotsByFaculty.getOrDefault(f.getId(), List.of()).stream()
                                .map(s -> s.getSubject().getProgramme().getCode())
                                .distinct().sorted().toList()))
                .toList();

        List<MetaContext.TeachingSubjectOption> teachingSubjects = new ArrayList<>();
        if (!user.isAdmin()) {
            Map<Long, MetaContext.TeachingSubjectOption> unique = new LinkedHashMap<>();
            slotsByFaculty.getOrDefault(user.id(), List.of()).stream()
                    .map(TeachingSlot::getSubject)
                    .sorted(java.util.Comparator.comparing(s -> s.getCode()))
                    .forEach(s -> unique.putIfAbsent(s.getId(), new MetaContext.TeachingSubjectOption(
                            s.getId(), s.getCode(), s.getName(), s.getStudyYear(),
                            s.getProgramme().getCode(), s.getProgramme().getName())));
            teachingSubjects.addAll(unique.values());
        }

        return new MetaContext(
                UserView.from(user),
                calendar.today().toString(),
                calendar.todayLabel(),
                calendar.academicYear(),
                user.campusName(),
                threshold,
                visibleProgrammes.stream()
                        .map(p -> new MetaContext.ProgrammeOption(p.getId(), p.getCode(), p.getName(), p.getDepartment()))
                        .toList(),
                years,
                faculty,
                teachingSubjects
        );
    }
}
