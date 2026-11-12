package com.campusone.config;

import com.campusone.domain.AttendanceRecord;
import com.campusone.domain.AttendanceSession;
import com.campusone.domain.AuditEvent;
import com.campusone.domain.Campus;
import com.campusone.domain.FeeAccount;
import com.campusone.domain.MarkEntry;
import com.campusone.domain.MarksComponent;
import com.campusone.domain.ModerationState;
import com.campusone.domain.Payment;
import com.campusone.domain.Programme;
import com.campusone.domain.Student;
import com.campusone.domain.Subject;
import com.campusone.domain.TeachingSlot;
import com.campusone.domain.User;
import com.campusone.repo.AttendanceRecordRepository;
import com.campusone.repo.AttendanceSessionRepository;
import com.campusone.repo.AuditEventRepository;
import com.campusone.repo.CampusRepository;
import com.campusone.repo.FeeAccountRepository;
import com.campusone.repo.MarkEntryRepository;
import com.campusone.repo.MarksComponentRepository;
import com.campusone.repo.ModerationStateRepository;
import com.campusone.repo.PaymentRepository;
import com.campusone.repo.ProgrammeRepository;
import com.campusone.repo.StudentRepository;
import com.campusone.repo.SubjectRepository;
import com.campusone.repo.TeachingSlotRepository;
import com.campusone.repo.UserRepository;
import com.campusone.service.AcademicCalendar;
import com.campusone.service.RiskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final CampusRepository campusRepository;
    private final ProgrammeRepository programmeRepository;
    private final SubjectRepository subjectRepository;
    private final TeachingSlotRepository teachingSlotRepository;
    private final StudentRepository studentRepository;
    private final MarksComponentRepository componentRepository;
    private final MarkEntryRepository markEntryRepository;
    private final ModerationStateRepository moderationStateRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final FeeAccountRepository feeAccountRepository;
    private final PaymentRepository paymentRepository;
    private final AuditEventRepository auditEventRepository;
    private final RiskService riskService;
    private final AcademicCalendar calendar;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final String facultyPassword;

    public DemoDataSeeder(UserRepository userRepository, CampusRepository campusRepository,
                          ProgrammeRepository programmeRepository, SubjectRepository subjectRepository,
                          TeachingSlotRepository teachingSlotRepository, StudentRepository studentRepository,
                          MarksComponentRepository componentRepository, MarkEntryRepository markEntryRepository,
                          ModerationStateRepository moderationStateRepository,
                          AttendanceSessionRepository sessionRepository,
                          AttendanceRecordRepository recordRepository, FeeAccountRepository feeAccountRepository,
                          PaymentRepository paymentRepository, AuditEventRepository auditEventRepository,
                          RiskService riskService, AcademicCalendar calendar, PasswordEncoder passwordEncoder,
                          @Value("${app.demo.admin-password}") String adminPassword,
                          @Value("${app.demo.faculty-password}") String facultyPassword) {
        this.userRepository = userRepository;
        this.campusRepository = campusRepository;
        this.programmeRepository = programmeRepository;
        this.subjectRepository = subjectRepository;
        this.teachingSlotRepository = teachingSlotRepository;
        this.studentRepository = studentRepository;
        this.componentRepository = componentRepository;
        this.markEntryRepository = markEntryRepository;
        this.moderationStateRepository = moderationStateRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.feeAccountRepository = feeAccountRepository;
        this.paymentRepository = paymentRepository;
        this.auditEventRepository = auditEventRepository;
        this.riskService = riskService;
        this.calendar = calendar;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.facultyPassword = facultyPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("CampusOne demo data already present; seed skipped.");
            return;
        }
        log.info("Seeding CampusOne demo data...");
        LocalDate today = calendar.today();
        Campus campus = campusRepository.save(new Campus("BBSR", "Bhubaneswar Campus", "Bhubaneswar"));

        Programme cse = programmeRepository.save(new Programme("BT-CSE", "B.Tech Computer Science",
                "Computer Science & Engineering", campus, 4));
        Programme me = programmeRepository.save(new Programme("BT-ME", "B.Tech Mechanical",
                "Mechanical Engineering", campus, 4));
        Programme mba = programmeRepository.save(new Programme("MBA", "Master of Business Administration",
                "Management Studies", campus, 2));

        User admin = userRepository.save(new User("Sasmita Nayak", "admin@campusone.demo",
                passwordEncoder.encode(adminPassword), User.Role.ADMIN, "Office administrator", campus));
        User meera = userRepository.save(new User("Dr. Meera Sahoo", "faculty@campusone.demo",
                passwordEncoder.encode(facultyPassword), User.Role.FACULTY,
                "Assistant Professor, Computer Science", campus));
        User rakesh = userRepository.save(new User("Prof. Rakesh Das", "rakesh.das@campusone.demo",
                passwordEncoder.encode(facultyPassword), User.Role.FACULTY,
                "Associate Professor, Mechanical", campus));
        User farah = userRepository.save(new User("Dr. Farah Khan", "farah.khan@campusone.demo",
                passwordEncoder.encode(facultyPassword), User.Role.FACULTY,
                "Assistant Professor, Management", campus));

        Subject ds = subjectRepository.save(new Subject("CSE301", "Data Structures", cse, 2, 4));
        Subject dbms = subjectRepository.save(new Subject("CSE302", "Database Systems", cse, 2, 3));
        Subject os = subjectRepository.save(new Subject("CSE303", "Operating Systems", cse, 2, 3));
        Subject thermo = subjectRepository.save(new Subject("ME201", "Thermodynamics", me, 2, 4));
        Subject marketing = subjectRepository.save(new Subject("MBA101", "Marketing Management", mba, 1, 3));

        List<TeachingSlot> slots = List.of(
                new TeachingSlot(ds, meera, DayOfWeek.MONDAY, 1, "B-204"),
                new TeachingSlot(ds, meera, DayOfWeek.WEDNESDAY, 2, "B-204"),
                new TeachingSlot(ds, meera, DayOfWeek.FRIDAY, 1, "B-204"),
                new TeachingSlot(dbms, meera, DayOfWeek.TUESDAY, 2, "B-204"),
                new TeachingSlot(dbms, meera, DayOfWeek.THURSDAY, 1, "B-204"),
                new TeachingSlot(os, meera, DayOfWeek.MONDAY, 3, "B-204"),
                new TeachingSlot(os, meera, DayOfWeek.THURSDAY, 3, "B-204"),
                new TeachingSlot(thermo, rakesh, DayOfWeek.TUESDAY, 1, "M-101"),
                new TeachingSlot(thermo, rakesh, DayOfWeek.FRIDAY, 2, "M-101"),
                new TeachingSlot(marketing, farah, DayOfWeek.WEDNESDAY, 1, "MBA-1"),
                new TeachingSlot(marketing, farah, DayOfWeek.FRIDAY, 3, "MBA-1"));
        teachingSlotRepository.saveAll(slots);

        int cseStartYear = today.getYear() - 2;
        int mbaStartYear = today.getYear() - 1;
        List<StudentSpec> specs = studentSpecs();
        Map<String, Student> studentsByRoll = new LinkedHashMap<>();
        int index = 0;
        for (StudentSpec spec : specs) {
            Programme programme = spec.programme().equals("CSE") ? cse : spec.programme().equals("ME") ? me : mba;
            LocalDate admitted = spec.programme().equals("MBA")
                    ? LocalDate.of(mbaStartYear, 8, 1) : LocalDate.of(cseStartYear, 8, 1);
            String email = spec.name().toLowerCase(Locale.ENGLISH).replace(" ", ".") + "@students.campusone.demo";
            String guardianPhone = "+91 9" + String.format(Locale.ENGLISH, "%09d", 800000000 + (index * 7919) % 199999999);
            Student student = studentRepository.save(new Student(spec.roll(), spec.name(), programme,
                    spec.year(), spec.section(), spec.batch(), spec.category(), spec.gender(), email,
                    spec.guardian(), guardianPhone, spec.consent(), admitted));
            studentsByRoll.put(spec.roll(), student);
            index++;
        }

        Map<String, StudentSpec> specByRoll = new HashMap<>();
        specs.forEach(spec -> specByRoll.put(spec.roll(), spec));

        List<MarksComponent> components = new ArrayList<>();
        for (Subject subject : List.of(ds, dbms, os, thermo, marketing)) {
            components.add(componentRepository.save(new MarksComponent(subject, "IA-1", 15, 0)));
            components.add(componentRepository.save(new MarksComponent(subject, "IA-2", 15, 1)));
            components.add(componentRepository.save(new MarksComponent(subject, "Assignment", 10, 2)));
        }
        Map<Long, List<MarksComponent>> componentsBySubject = new HashMap<>();
        components.forEach(c -> componentsBySubject.computeIfAbsent(c.getSubject().getId(), k -> new ArrayList<>()).add(c));

        Map<Long, List<Student>> rosterBySubject = new HashMap<>();
        for (Subject subject : List.of(ds, dbms, os, thermo, marketing)) {
            rosterBySubject.put(subject.getId(), studentRepository
                    .findByProgrammeIdAndStudyYearAndActiveTrueOrderByName(
                            subject.getProgramme().getId(), subject.getStudyYear()));
        }

        seedAttendance(slots, rosterBySubject, specByRoll, today);
        seedMarks(studentsByRoll, subjectRepository.findAll(), componentsBySubject, rosterBySubject, admin, meera,
                dbms, ds, today);
        seedFees(studentsByRoll, specByRoll, today);
        auditEventRepository.save(audit("Sasmita Nayak", admin.getId(), "SEED_IMPORTED", "CAMPUS", campus.getId().toString(),
                "Synthetic demo data seeded for Bhubaneswar Campus · 23 students · 5 subjects", today.atTime(8, 30)));
        riskService.recomputeInternal();
        log.info("CampusOne seed complete: {} students, attendance and fee history ready.", specs.size());
    }

    private void seedAttendance(List<TeachingSlot> slots, Map<Long, List<Student>> rosterBySubject,
                                Map<String, StudentSpec> specByRoll, LocalDate today) {
        List<AttendanceSession> sessions = new ArrayList<>();
        LocalDate start = today.minusWeeks(8);
        LocalDate end = today.minusDays(1);
        for (TeachingSlot slot : slots) {
            LocalDate date = start;
            while (!date.isAfter(end)) {
                if (date.getDayOfWeek() == slot.getDayOfWeek()) {
                    AttendanceSession session = new AttendanceSession(slot.getSubject(), slot.getFaculty(), date,
                            slot.getPeriod(), null);
                    session.setSubmittedAt(date.atTime(17, 10).atZone(IST).toInstant());
                    sessions.add(session);
                }
                date = date.plusDays(1);
            }
        }
        sessionRepository.saveAll(sessions);

        Map<Long, List<AttendanceSession>> sessionsByRoster = new HashMap<>();
        for (AttendanceSession session : sessions) {
            Long subjectId = session.getSubject().getId();
            for (Student student : rosterBySubject.get(subjectId)) {
                sessionsByRoster.computeIfAbsent(student.getId(), k -> new ArrayList<>()).add(session);
            }
        }

        Map<Long, Set<Long>> absentSessionIds = new HashMap<>();
        for (Student student : studentRepository.findAll()) {
            StudentSpec spec = specByRoll.get(student.getRollNo());
            if (spec == null) {
                continue;
            }
            List<AttendanceSession> eligible = sessionsByRoster.getOrDefault(student.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(AttendanceSession::getDate).thenComparingInt(AttendanceSession::getPeriod))
                    .toList();
            if (eligible.isEmpty()) {
                continue;
            }
            int total = eligible.size();
            int absentCount = (int) Math.ceil((100.0 - spec.attendanceTarget()) / 100.0 * total);
            absentCount = Math.max(0, Math.min(total, absentCount));
            Set<Integer> absentIndexes = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(spec.recentAbsences(), absentCount); i++) {
                absentIndexes.add(total - 1 - i);
            }
            int guard = 0;
            while (absentIndexes.size() < absentCount && guard < total * 3) {
                int idx = (int) Math.floor((absentIndexes.size() + 0.5) * total / Math.max(1, absentCount));
                idx = Math.floorMod(idx + student.getRollNo().hashCode(), total);
                while (absentIndexes.contains(idx)) {
                    idx = (idx + 1) % total;
                }
                absentIndexes.add(idx);
                guard++;
            }
            Set<Long> ids = new HashSet<>();
            absentIndexes.forEach(i -> ids.add(eligible.get(i).getId()));
            absentSessionIds.put(student.getId(), ids);
        }

        List<AttendanceRecord> records = new ArrayList<>();
        for (AttendanceSession session : sessions) {
            for (Student student : rosterBySubject.get(session.getSubject().getId())) {
                AttendanceRecord.Status status = absentSessionIds.getOrDefault(student.getId(), Set.of())
                        .contains(session.getId()) ? AttendanceRecord.Status.ABSENT : AttendanceRecord.Status.PRESENT;
                records.add(new AttendanceRecord(session, student, status));
            }
        }
        recordRepository.saveAll(records);
    }

    private void seedMarks(Map<String, Student> studentsByRoll, List<Subject> subjects,
                           Map<Long, List<MarksComponent>> componentsBySubject,
                           Map<Long, List<Student>> rosterBySubject, User admin, User meera, Subject dbms,
                           Subject ds, LocalDate today) {
        Map<Long, Subject> subjectById = new HashMap<>();
        subjects.forEach(s -> subjectById.put(s.getId(), s));
        List<MarkEntry> entries = new ArrayList<>();
        for (Student student : studentsByRoll.values()) {
            StudentSpec spec = specByRoll(student.getRollNo());
            List<Subject> studentSubjects = subjects.stream()
                    .filter(s -> s.getProgramme().getId().equals(student.getProgramme().getId())
                            && s.getStudyYear() == student.getStudyYear())
                    .sorted(Comparator.comparing(Subject::getCode))
                    .toList();
            for (int i = 0; i < studentSubjects.size(); i++) {
                Subject subject = studentSubjects.get(i);
                int delta = studentSubjects.size() == 1 ? 0 : (i == 0 ? 1 : i == studentSubjects.size() - 1 ? -1 : 0);
                int target = Math.max(0, Math.min(40, spec.marksTarget() + delta));
                List<MarksComponent> comps = componentsBySubject.get(subject.getId()).stream()
                        .sorted(Comparator.comparingInt(MarksComponent::getSortOrder)).toList();
                int assignment = Math.max(3, Math.min(10, (int) Math.round(target * 0.2)));
                int remaining = target - assignment;
                if (remaining > 30) {
                    assignment = Math.min(10, assignment + (remaining - 30));
                    remaining = target - assignment;
                }
                int ia1 = Math.min(15, (int) Math.round(remaining / 2.0));
                int ia2 = Math.min(15, remaining - ia1);
                List<Integer> split = List.of(ia1, ia2, assignment);
                for (int c = 0; c < comps.size(); c++) {
                    MarksComponent component = comps.get(c);
                    BigDecimal score = BigDecimal.valueOf(Math.min(component.getMaxScore(), split.get(c)));
                    MarkEntry entry = new MarkEntry(student, component, score, facultyFor(subject));
                    entries.add(entry);
                }
            }
        }
        markEntryRepository.saveAll(entries);

        ModerationState locked = moderationStateRepository.save(new ModerationState(dbms));
        locked.setStatus(ModerationState.Status.LOCKED);
        locked.setLockedBy(admin.getName());
        locked.setLockedAt(today.minusDays(2).atTime(16, 20).atZone(IST).toInstant());
        locked.setNote("Mid-semester moderation complete. Internal totals checked against attendance.");
        moderationStateRepository.save(locked);
        subjectRepository.findAll().stream()
                .filter(s -> !s.getId().equals(dbms.getId()))
                .forEach(s -> moderationStateRepository.save(new ModerationState(s)));

        auditEventRepository.save(audit("Dr. Meera Sahoo", meera.getId(), "MARKS_IMPORTED", "SUBJECT_MARKS",
                ds.getId().toString(), "CSE301 · internal marks imported from department sheet", today.minusDays(4).atTime(11, 5)));
        auditEventRepository.save(audit("Dr. Meera Sahoo", meera.getId(), "MARK_UPDATED", "SUBJECT_MARKS",
                ds.getId().toString(), "ENG-24-CSE-0418 · IA-2: 8 → 10", today.minusDays(3).atTime(15, 45)));
        auditEventRepository.save(audit(admin.getName(), admin.getId(), "MODERATION_LOCKED", "SUBJECT_MARKS",
                dbms.getId().toString(), "CSE302 moderated and locked · Mid-semester moderation complete.",
                today.minusDays(2).atTime(16, 20)));
    }

    private void seedFees(Map<String, Student> studentsByRoll, Map<String, StudentSpec> specByRoll, LocalDate today) {
        BigDecimal cseFee = new BigDecimal("120000");
        BigDecimal meFee = new BigDecimal("110000");
        BigDecimal mbaFee = new BigDecimal("150000");
        List<FeeAccount> accounts = new ArrayList<>();
        int i = 0;
        for (Student student : studentsByRoll.values()) {
            StudentSpec spec = specByRoll.get(student.getRollNo());
            BigDecimal gross = student.getProgramme().getCode().equals("BT-CSE") ? cseFee
                    : student.getProgramme().getCode().equals("BT-ME") ? meFee : mbaFee;
            BigDecimal scholarship = spec.scholarship();
            BigDecimal due = spec.due();
            BigDecimal paid = gross.subtract(scholarship).subtract(due);
            if (paid.signum() < 0) {
                paid = BigDecimal.ZERO;
                due = gross.subtract(scholarship);
            }
            LocalDate dueDate = spec.overdueDays() > 0
                    ? today.minusDays(spec.overdueDays()) : today.plusDays(22 + (i % 9));
            accounts.add(feeAccountRepository.save(new FeeAccount(student, calendar.academicYear(), gross,
                    scholarship, paid, due, dueDate)));
            i++;
        }
        int p = 0;
        for (FeeAccount account : accounts) {
            if (account.getPaidAmount().signum() <= 0) {
                continue;
            }
            BigDecimal first = account.getPaidAmount().multiply(new BigDecimal("0.6"))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal second = account.getPaidAmount().subtract(first);
            LocalDate firstDate = today.minusDays(8 + (p * 11) % 47);
            Payment one = new Payment(account, first, Payment.Method.UPI, receipt(p, "A"),
                    "SEED-" + account.getStudent().getRollNo() + "-1", "Sasmita Nayak");
            one.setPaidAt(firstDate.atTime(11, 20).atZone(IST).toInstant());
            paymentRepository.save(one);
            if (second.signum() > 0) {
                Payment two = new Payment(account, second, Payment.Method.NETBANKING, receipt(p, "B"),
                        "SEED-" + account.getStudent().getRollNo() + "-2", "Sasmita Nayak");
                two.setPaidAt(firstDate.plusDays(4).atTime(15, 5).atZone(IST).toInstant());
                paymentRepository.save(two);
            }
            p++;
        }
    }

    private String receipt(int index, String suffix) {
        return "C1-SEED-" + String.format(Locale.ENGLISH, "%03d", index + 1) + "-" + suffix;
    }

    private StudentSpec specByRoll(String roll) {
        return studentSpecs().stream().filter(s -> s.roll().equals(roll)).findFirst().orElseThrow();
    }

    private String facultyFor(Subject subject) {
        return switch (subject.getProgramme().getCode()) {
            case "BT-CSE" -> "Dr. Meera Sahoo";
            case "BT-ME" -> "Prof. Rakesh Das";
            default -> "Dr. Farah Khan";
        };
    }

    private AuditEvent audit(String actor, Long actorId, String action, String entityType, String entityId,
                             String detail, java.time.LocalDateTime at) {
        AuditEvent event = new AuditEvent(actor, actorId, action, entityType, entityId, detail);
        event.setCreatedAt(at.atZone(IST).toInstant());
        return event;
    }

    private List<StudentSpec> studentSpecs() {
        return List.of(
                new StudentSpec("ENG-24-CSE-0412", "Subhashree Mohanty", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Prakash Mohanty",
                        Student.ConsentStatus.CONSENTED, 81.5, 33, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-CSE-0418", "Ritesh Behera", "CSE", 2, "A", "2024-28",
                        Student.Category.OBC, Student.Gender.MALE, "Sanjaya Behera",
                        Student.ConsentStatus.CONSENTED, 74.6, 24, new BigDecimal("12000"),
                        BigDecimal.ZERO, 0, 2),
                new StudentSpec("ENG-24-CSE-0405", "Sneha Patra", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Asit Patra",
                        Student.ConsentStatus.CONSENTED, 71.2, 19, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 3),
                new StudentSpec("ENG-24-CSE-0421", "Aditya Mohapatra", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.MALE, "Bibhuti Mohapatra",
                        Student.ConsentStatus.CONSENTED, 88.4, 35, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-CSE-0409", "Ipsita Das", "CSE", 2, "A", "2024-28",
                        Student.Category.SC, Student.Gender.FEMALE, "Pradip Das",
                        Student.ConsentStatus.CONSENTED, 92.1, 37, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-CSE-0427", "Omkar Sahu", "CSE", 2, "A", "2024-28",
                        Student.Category.OBC, Student.Gender.MALE, "Dushmanta Sahu",
                        Student.ConsentStatus.CONSENTED, 76.0, 27, new BigDecimal("18000"),
                        BigDecimal.ZERO, 25, 0),
                new StudentSpec("ENG-24-CSE-0415", "Ananya Rath", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Sarat Rath",
                        Student.ConsentStatus.CONSENTED, 84.3, 31, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-CSE-0430", "Debasish Nayak", "CSE", 2, "A", "2024-28",
                        Student.Category.ST, Student.Gender.MALE, "Kailash Nayak",
                        Student.ConsentStatus.CONSENTED, 66.8, 18, new BigDecimal("38000"),
                        BigDecimal.ZERO, 6, 3),
                new StudentSpec("ENG-24-CSE-0422", "Pragyan Jena", "CSE", 2, "A", "2024-28",
                        Student.Category.EWS, Student.Gender.FEMALE, "Prafulla Jena",
                        Student.ConsentStatus.CONSENTED, 79.5, 29, new BigDecimal("9000"),
                        BigDecimal.ZERO, 30, 1),
                new StudentSpec("ENG-24-CSE-0411", "Saswat Mishra", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.MALE, "Debashis Mishra",
                        Student.ConsentStatus.CONSENTED, 73.1, 23, new BigDecimal("45000"),
                        BigDecimal.ZERO, 13, 1),
                new StudentSpec("ENG-24-CSE-0402", "Tanmayee Pradhan", "CSE", 2, "A", "2024-28",
                        Student.Category.SC, Student.Gender.FEMALE, "Raghunath Pradhan",
                        Student.ConsentStatus.CONSENTED, 90.0, 34, new BigDecimal("0"),
                        new BigDecimal("30000"), 0, 0),
                new StudentSpec("ENG-24-CSE-0425", "Harish Kumar", "CSE", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.MALE, "Mahesh Kumar",
                        Student.ConsentStatus.CONSENTED, 86.7, 32, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-ME-0177", "Ankit Panda", "ME", 2, "A", "2024-28",
                        Student.Category.OBC, Student.Gender.MALE, "Pramod Panda",
                        Student.ConsentStatus.CONSENTED, 68.2, 21, new BigDecimal("45000"),
                        BigDecimal.ZERO, 13, 2),
                new StudentSpec("ENG-24-ME-0181", "Rojalin Behera", "ME", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Bijay Behera",
                        Student.ConsentStatus.CONSENTED, 87.4, 34, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-ME-0172", "Sameer Rout", "ME", 2, "A", "2024-28",
                        Student.Category.SC, Student.Gender.MALE, "Susanta Rout",
                        Student.ConsentStatus.CONSENTED, 72.5, 23, new BigDecimal("15000"),
                        BigDecimal.ZERO, 22, 0),
                new StudentSpec("ENG-24-ME-0188", "Priyanka Das", "ME", 2, "A", "2024-28",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Kabita Das",
                        Student.ConsentStatus.CONSENTED, 90.1, 35, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("ENG-24-ME-0175", "Bikash Sethi", "ME", 2, "A", "2024-28",
                        Student.Category.ST, Student.Gender.MALE, "Sudhir Sethi",
                        Student.ConsentStatus.CONSENTED, 77.2, 28, new BigDecimal("22000"),
                        BigDecimal.ZERO, 9, 0),
                new StudentSpec("ENG-24-ME-0164", "Anwesha Nayak", "ME", 2, "A", "2024-28",
                        Student.Category.OBC, Student.Gender.FEMALE, "Ramesh Nayak",
                        Student.ConsentStatus.CONSENTED, 83.9, 30, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("MBA-25-0093", "Farheen Ali", "MBA", 1, "A", "2025-27",
                        Student.Category.GENERAL, Student.Gender.FEMALE, "Imran Ali",
                        Student.ConsentStatus.CONSENTED, 92.0, 36, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("MBA-25-0088", "Arjun Senapati", "MBA", 1, "A", "2025-27",
                        Student.Category.GENERAL, Student.Gender.MALE, "Pradeep Senapati",
                        Student.ConsentStatus.CONSENTED, 85.5, 31, new BigDecimal("25000"),
                        BigDecimal.ZERO, 18, 0),
                new StudentSpec("MBA-25-0097", "Meghna Patnaik", "MBA", 1, "A", "2025-27",
                        Student.Category.OBC, Student.Gender.FEMALE, "Sanjay Patnaik",
                        Student.ConsentStatus.CONSENTED, 78.4, 27, new BigDecimal("0"),
                        BigDecimal.ZERO, 0, 0),
                new StudentSpec("MBA-25-0091", "Rohan Sahoo", "MBA", 1, "A", "2025-27",
                        Student.Category.GENERAL, Student.Gender.MALE, "Biswajit Sahoo",
                        Student.ConsentStatus.PENDING, 74.9, 23, new BigDecimal("30000"),
                        BigDecimal.ZERO, 27, 1),
                new StudentSpec("MBA-25-0102", "Swayam Mishra", "MBA", 1, "A", "2025-27",
                        Student.Category.SC, Student.Gender.MALE, "Krushna Mishra",
                        Student.ConsentStatus.PENDING, 88.0, 33, new BigDecimal("12000"),
                        new BigDecimal("20000"), 26, 0));
    }

    private record StudentSpec(
            String roll,
            String name,
            String programme,
            int year,
            String section,
            String batch,
            Student.Category category,
            Student.Gender gender,
            String guardian,
            Student.ConsentStatus consent,
            double attendanceTarget,
            int marksTarget,
            BigDecimal due,
            BigDecimal scholarship,
            int overdueDays,
            int recentAbsences
    ) {
    }
}
