package com.campusone.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RiskScoringModel {
    public record Assessment(int score, String band, List<String> reasons) {
    }

    public Assessment assess(Double attendancePercent, Double marksPercent, double trendDelta) {
        double attendancePart = attendancePercent == null ? 0
                : clamp((90.0 - attendancePercent) / 25.0 * 45.0, 0, 45);
        double marksPart = marksPercent == null ? 0
                : clamp((75.0 - marksPercent) / 30.0 * 45.0, 0, 45);
        double trendPart = clamp(-trendDelta * 0.5, 0, 10);
        int score = (int) Math.round(attendancePart + marksPart + trendPart);
        String band = score >= 70 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";

        List<String> reasons = new ArrayList<>();
        if (attendancePercent != null && attendancePercent < 75) {
            reasons.add("Attendance " + format(attendancePercent) + "% — below the 75% rule");
        }
        if (marksPercent != null && marksPercent < 60) {
            reasons.add("Internal marks " + format(marksPercent) + "% — below the 60% watch line");
        }
        if (trendDelta <= -8) {
            reasons.add("Attendance fell " + format(Math.abs(trendDelta)) + " points across the last four classes");
        }
        if (reasons.isEmpty() && score >= 50) {
            reasons.add("Attendance and internal marks are both in the watch range");
        }
        return new Assessment(score, band, reasons);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String format(double value) {
        return String.format(java.util.Locale.ENGLISH, "%.1f", value);
    }
}
