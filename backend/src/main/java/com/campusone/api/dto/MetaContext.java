package com.campusone.api.dto;

import java.util.List;

public record MetaContext(
        UserView user,
        String today,
        String todayLabel,
        String academicYear,
        String campusName,
        int attendanceThreshold,
        List<ProgrammeOption> programmes,
        List<Integer> years,
        List<FacultyOption> faculty,
        List<TeachingSubjectOption> teachingSubjects
) {
    public record ProgrammeOption(Long id, String code, String name, String department) {
    }

    public record FacultyOption(Long id, String name, String title, List<String> programmeCodes) {
    }

    public record TeachingSubjectOption(Long id, String code, String name, int studyYear, String programmeCode,
                                        String programmeName) {
    }
}
