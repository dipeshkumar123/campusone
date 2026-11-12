package com.campusone.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuestionParser {
    public record ParsedQuestion(
            boolean refused,
            String refusalMessage,
            boolean clarification,
            String clarificationMessage,
            List<String> clarificationOptions,
            String programmeCode,
            Integer studyYear,
            Double attendanceBelow,
            Double marksBelowPercent,
            String marksFilterLabel,
            BigDecimal dueAbove,
            boolean atRisk,
            boolean consentPending,
            boolean thisMonth,
            boolean averageMarks,
            boolean countOnly,
            boolean explicitAll,
            List<String> notes
    ) {
    }

    private static final Pattern WRITE_VERBS = Pattern.compile(
            "\\b(create|update|delete|drop|insert|change|edit|waive|remove|revoke|approve|lock|unlock|refund|assign|send|pay|mark\\s+attendance)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BELOW_NUMBER = Pattern.compile(
            "\\b(?:below|under|less\\s+than|lower\\s+than)\\s+([0-9]{1,3}(?:\\.[0-9]+)?)\\s*%?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ABOVE_ATTENDANCE = Pattern.compile(
            "\\b(?:above|over|more\\s+than|at\\s+least)\\s+[0-9]{1,3}(?:\\.[0-9]+)?\\s*%",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "\\b(?:above|over|more\\s+than|greater\\s+than)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]{2,})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_WORD = Pattern.compile(
            "\\b(first|second|third|fourth|1st|2nd|3rd|4th)[\\s-]*(?:year|yr)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_NUMBER = Pattern.compile("\\byear\\s*([1-4])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTENDANCE = Pattern.compile(
            "\\b(attendance|absent|attending|present|shortage)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS = Pattern.compile(
            "\\b(marks?|scores?|internal|internals)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEES = Pattern.compile(
            "\\b(fee|fees|dues?|outstanding|unpaid|pending\\s+payment)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RISK = Pattern.compile(
            "\\b(at[\\s-]?risk|risk\\s+flags?|watchlist)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSENT = Pattern.compile("\\b(consent|guardian\\s+approval)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AVERAGE = Pattern.compile("\\b(average|avg|mean)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile("\\b(how\\s+many|count|number\\s+of)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_LIST = Pattern.compile(
            "\\b(list|show|which|who|students?|everyone|all)\\b", Pattern.CASE_INSENSITIVE);

    public ParsedQuestion parse(String rawQuestion) {
        String q = rawQuestion == null ? "" : rawQuestion.trim().toLowerCase(Locale.ENGLISH);
        List<String> examples = defaultExamples();

        if (q.isBlank()) {
            return clarify("Ask a question about students, attendance, internal marks, fees or risk.",
                    examples, List.of());
        }
        Matcher write = WRITE_VERBS.matcher(q);
        if (write.find()) {
            return refuse("I can only read data. CampusOne Ask cannot create, edit, lock, waive or send anything. "
                    + "Use the Marks, Fees or At-risk screens for changes, where the action is logged.",
                    examples);
        }
        if (q.contains("pharmacy") || q.contains("b.pharm")) {
            return clarify("Pharmacy is not part of this core campus slice. The demo currently holds B.Tech CSE, "
                    + "B.Tech Mechanical and MBA records.", examples, List.of());
        }

        boolean pharmacy = false;
        boolean mentionsAttendance = ATTENDANCE.matcher(q).find();
        boolean mentionsMarks = MARKS.matcher(q).find();
        boolean mentionsFees = FEES.matcher(q).find();
        boolean atRisk = RISK.matcher(q).find();
        boolean consent = CONSENT.matcher(q).find();
        boolean thisMonth = q.contains("this month") || q.contains("current month");
        boolean countOnly = COUNT.matcher(q).find();
        boolean averageMarks = AVERAGE.matcher(q).find() && mentionsMarks;
        boolean explicitAll = EXPLICIT_LIST.matcher(q).find();

        if (q.contains("paid") && !q.contains("unpaid") && !q.contains("not paid")
                && !q.contains("due") && !q.contains("outstanding")) {
            return clarify("I can show outstanding dues and defaulters, but not individual paid receipts yet. "
                    + "Try “Which students have fee dues?”", examples, List.of());
        }
        if (AVERAGE.matcher(q).find() && !mentionsMarks) {
            return clarify("I can average internal marks. For attendance, ask a count such as "
                    + "“How many second-year students are below 75% attendance?”", examples, List.of());
        }

        String programmeCode = null;
        String programmeNote = null;
        if (Pattern.compile("\\b(cse|computer\\s+science)\\b", Pattern.CASE_INSENSITIVE).matcher(q).find()) {
            programmeCode = "BT-CSE";
        } else if (Pattern.compile("\\b(mechanical|b\\.tech\\s+me)\\b", Pattern.CASE_INSENSITIVE).matcher(q).find()) {
            programmeCode = "BT-ME";
        } else if (Pattern.compile("\\b(mba|management\\s+studies|business\\s+school)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(q).find()) {
            programmeCode = "MBA";
        }

        Integer studyYear = null;
        Matcher yearWord = YEAR_WORD.matcher(q);
        if (yearWord.find()) {
            studyYear = switch (yearWord.group(1).toLowerCase(Locale.ENGLISH)) {
                case "first", "1st" -> 1;
                case "second", "2nd" -> 2;
                case "third", "3rd" -> 3;
                case "fourth", "4th" -> 4;
                default -> null;
            };
        } else {
            Matcher yearNumber = YEAR_NUMBER.matcher(q);
            if (yearNumber.find()) {
                studyYear = Integer.parseInt(yearNumber.group(1));
            }
        }

        boolean hasPercent = q.contains("%");
        Matcher below = BELOW_NUMBER.matcher(q);
        Double threshold = below.find() ? Double.parseDouble(below.group(1)) : null;
        Double attendanceBelow = null;
        Double marksBelowPercent = null;
        String marksFilterLabel = null;
        List<String> notes = new ArrayList<>();

        if (marksBelowPercent == null && mentionAttendanceExplicit(q) && mentionsMarks && threshold != null) {
            return clarify("You mentioned both attendance and marks with one cutoff. Which did you mean?",
                    List.of(
                            "How many second-year students are below 75% attendance this month?",
                            "How many second-year students have internal marks below 20 out of 40?"),
                    List.of(
                            "How many students are below 75% attendance?",
                            "How many students have internal marks below 20 out of 40?"));
        }

        if (threshold != null) {
            if (mentionsMarks && !mentionsAttendance) {
                if (hasPercent || threshold > 40) {
                    if (threshold > 100) {
                        return clarify("Marks percentages must be between 0 and 100.", examples, List.of());
                    }
                    marksBelowPercent = threshold;
                    marksFilterLabel = trimNumber(threshold) + "%";
                } else {
                    marksBelowPercent = threshold / 40.0 * 100.0;
                    marksFilterLabel = trimNumber(threshold) + " / 40";
                }
            } else if (mentionsAttendance) {
                if (threshold > 100) {
                    return clarify("Attendance percentages must be between 0 and 100.", examples, List.of());
                }
                attendanceBelow = threshold;
            } else if (hasPercent) {
                if (threshold > 100) {
                    return clarify("Attendance percentages must be between 0 and 100.", examples, List.of());
                }
                attendanceBelow = threshold;
                mentionsAttendance = true;
                notes.add("Read “below " + trimNumber(threshold) + "%” as attendance percentage.");
            } else {
                return clarify("I can read this cutoff as attendance or internal marks. Which one?",
                        List.of(
                                "How many second-year students are below 75% attendance this month?",
                                "How many students have internal marks below 20 out of 40?"),
                        List.of(
                                "How many students are below 75% attendance?",
                                "How many students have internal marks below 20 out of 40?"));
            }
        }

        if (attendanceBelow == null && mentionsAttendance && ABOVE_ATTENDANCE.matcher(q).find()) {
            return clarify("I can shortlist shortages below a cutoff (for example, below 75% attendance). "
                    + "Above-cutoff queries are not available yet.", examples, List.of());
        }

        BigDecimal dueAbove = null;
        if (mentionsFees) {
            Matcher amount = AMOUNT.matcher(q);
            if (amount.find()) {
                try {
                    dueAbove = new BigDecimal(amount.group(1).replace(",", ""));
                } catch (NumberFormatException ex) {
                    return clarify("I could not read that fee amount. Try “above 40000”.", examples, List.of());
                }
            } else if (q.contains("due") || q.contains("outstanding") || q.contains("unpaid")
                    || q.contains("not paid") || q.contains("pending")) {
                dueAbove = BigDecimal.ZERO;
            } else {
                return clarify("Ask about outstanding fee dues, for example “Which students have fee dues above ₹40,000?”",
                        examples, List.of());
            }
        }

        boolean consentPending = consent;
        boolean explicitFilter = programmeCode != null || studyYear != null || attendanceBelow != null
                || marksBelowPercent != null || dueAbove != null || atRisk || consentPending || averageMarks;
        boolean broadAsk = explicitAll || countOnly;

        if (!explicitFilter && !broadAsk) {
            return clarify("I can answer questions about attendance, internal marks, fee dues, at-risk students or "
                    + "guardian consent. Add a year, programme or cutoff.", examples, List.of());
        }
        if (pharmacy) {
            programmeNote = "Pharmacy is not loaded.";
        }
        if (notes.isEmpty() && mentionsAttendance && thisMonth) {
            notes.add("Attendance is scoped to the current month.");
        }

        return new ParsedQuestion(false, null, false, null, List.of(), programmeCode, studyYear,
                attendanceBelow, marksBelowPercent, marksFilterLabel, dueAbove, atRisk, consentPending,
                thisMonth, averageMarks, countOnly, explicitAll, appendNote(notes, programmeNote));
    }

    private boolean mentionAttendanceExplicit(String q) {
        return Pattern.compile("\\battendance\\b", Pattern.CASE_INSENSITIVE).matcher(q).find();
    }

    private List<String> appendNote(List<String> notes, String note) {
        if (note != null) {
            notes.add(note);
        }
        return notes;
    }

    private ParsedQuestion refuse(String message, List<String> examples) {
        return new ParsedQuestion(true, message, false, null, List.of(), null, null, null, null, null, null,
                false, false, false, false, false, false, List.of());
    }

    private ParsedQuestion clarify(String message, List<String> examples, List<String> options) {
        return new ParsedQuestion(false, null, true, message, options, null, null, null, null, null,
                null, false, false, false, false, false, false, List.of());
    }

    public static List<String> defaultExamples() {
        return List.of(
                "How many second-year students are below 75% attendance this month?",
                "Which students have fee dues above ₹40,000?",
                "Show at-risk students in CSE",
                "Average internal marks for second-year CSE",
                "Which students have guardian consent pending?");
    }

    private String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
