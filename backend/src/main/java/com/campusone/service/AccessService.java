package com.campusone.service;

import com.campusone.common.ApiException;
import com.campusone.config.AuthenticatedUser;
import com.campusone.config.CurrentUserService;
import com.campusone.domain.Programme;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.domain.TeachingSlot;
import com.campusone.domain.User;
import com.campusone.repo.ProgrammeRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.TeachingSlotRepository;
import com.campusone.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AccessService {
    private final CurrentUserService currentUserService;
    private final TeachingSlotRepository teachingSlotRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final ProgrammeRepository programmeRepository;
    private final UserRepository userRepository;

    public AccessService(CurrentUserService currentUserService, TeachingSlotRepository teachingSlotRepository,
                         StudentRepository studentRepository, SubjectRepository subjectRepository,
                         ProgrammeRepository programmeRepository, UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.teachingSlotRepository = teachingSlotRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.programmeRepository = programmeRepository;
        this.userRepository = userRepository;
    }

    public User userEntity() {
        return userRepository.findById(user().id())
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }

    public void requireAdmin() {
        currentUserService.requireAdmin();
    }

    public AuthenticatedUser user() {
        return currentUserService.current();
    }

    @Transactional(readOnly = true)
    public Set<Long> visibleProgrammeIds() {
        AuthenticatedUser user = user();
        if (user.isAdmin()) {
            return programmeRepository.findAll().stream().map(Programme::getId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return teachingSlotRepository.findByFacultyIdOrderByPeriod(user.id()).stream()
                .map(TeachingSlot::getSubject)
                .map(Subject::getProgramme)
                .map(Programme::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public List<Student> visibleStudents() {
        Set<Long> programmeIds = visibleProgrammeIds();
        return studentRepository.findByActiveTrueOrderByName().stream()
                .filter(student -> programmeIds.contains(student.getProgramme().getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canTeachSubject(Long subjectId) {
        AuthenticatedUser user = user();
        return !user.isAdmin() && teachingSlotRepository.existsByFacultyIdAndSubjectId(user.id(), subjectId);
    }

    @Transactional(readOnly = true)
    public boolean canViewSubject(Long subjectId) {
        if (user().isAdmin()) {
            return true;
        }
        return teachingSlotRepository.existsByFacultyIdAndSubjectId(user().id(), subjectId);
    }

    @Transactional(readOnly = true)
    public Subject requireViewableSubject(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject not found."));
        if (!canViewSubject(subjectId)) {
            throw ApiException.forbidden("This subject is outside your teaching scope.");
        }
        return subject;
    }

    @Transactional(readOnly = true)
    public Subject requireTeachSubject(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject not found."));
        if (user().isAdmin()) {
            throw ApiException.forbidden("Teachers enter attendance and marks. Office admins moderate and review.");
        }
        if (!teachingSlotRepository.existsByFacultyIdAndSubjectId(user().id(), subjectId)) {
            throw ApiException.forbidden("You are not assigned to this subject.");
        }
        return subject;
    }

    public void assertCanView(Student student) {
        if (user().isAdmin()) {
            return;
        }
        Set<Long> ids = visibleProgrammeIds();
        if (!ids.contains(student.getProgramme().getId())) {
            throw ApiException.forbidden("This student is outside your programme scope.");
        }
    }
}
