package com.campusone.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AcademicCalendar {
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);

    private final Clock clock;

    public AcademicCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public String todayLabel() {
        return today().format(DATE_LABEL);
    }

    public String academicYear() {
        LocalDate date = today();
        int start = date.getMonthValue() >= 6 ? date.getYear() : date.getYear() - 1;
        return start + "-" + String.valueOf(start + 1).substring(2);
    }
}
