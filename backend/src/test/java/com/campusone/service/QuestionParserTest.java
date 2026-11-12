package com.campusone.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionParserTest {
    private final QuestionParser parser = new QuestionParser();

    @Test
    void parsesAttendanceQuestionWithProgrammeAndYear() {
        var parsed = parser.parse("Which second-year CSE students have attendance below 75%?");

        assertThat(parsed.refused()).isFalse();
        assertThat(parsed.clarification()).isFalse();
        assertThat(parsed.programmeCode()).isEqualTo("BT-CSE");
        assertThat(parsed.studyYear()).isEqualTo(2);
        assertThat(parsed.attendanceBelow()).isEqualTo(75.0);
    }

    @Test
    void parsesFeeDuesAboveAmount() {
        var parsed = parser.parse("Show fee dues above 25000");

        assertThat(parsed.refused()).isFalse();
        assertThat(parsed.dueAbove()).isEqualByComparingTo(new BigDecimal("25000"));
    }

    @Test
    void parsesAtRiskCountQuestion() {
        var parsed = parser.parse("How many first-year MBA students are at risk?");

        assertThat(parsed.atRisk()).isTrue();
        assertThat(parsed.programmeCode()).isEqualTo("MBA");
        assertThat(parsed.studyYear()).isEqualTo(1);
        assertThat(parsed.countOnly()).isTrue();
    }

    @Test
    void refusesWriteRequests() {
        var parsed = parser.parse("Update the marks for CSE301 to full score");

        assertThat(parsed.refused()).isTrue();
        assertThat(parsed.refusalMessage()).contains("read data").contains("Marks");
    }

    @Test
    void clarifiesInsteadOfGuessingPaidFees() {
        var parsed = parser.parse("Which students have paid fees?");

        assertThat(parsed.clarification()).isTrue();
        assertThat(parsed.clarificationMessage()).contains("outstanding");
    }

    @Test
    void clarifiesWhenPharmacyIsOutsideTheSlice() {
        var parsed = parser.parse("Which B.Pharm students are at risk?");

        assertThat(parsed.clarification()).isTrue();
        assertThat(parsed.clarificationMessage()).contains("Pharmacy");
    }

    @Test
    void emptyQuestionAsksForClarification() {
        var parsed = parser.parse("   ");

        assertThat(parsed.clarification()).isTrue();
        assertThat(parsed.clarificationMessage()).contains("Ask a question");
    }

    @Test
    void parsesAverageMarksQuestion() {
        var parsed = parser.parse("What is the average internal marks for CSE?");

        assertThat(parsed.averageMarks()).isTrue();
        assertThat(parsed.programmeCode()).isEqualTo("BT-CSE");
        assertThat(parsed.clarification()).isFalse();
    }
}
