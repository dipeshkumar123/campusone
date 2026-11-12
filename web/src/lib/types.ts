export type Role = 'ADMIN' | 'FACULTY';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  roleLabel: string;
  title: string;
  campusId: number;
  campusName: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

export interface ProgrammeOption {
  id: number;
  code: string;
  name: string;
  department: string;
}

export interface FacultyOption {
  id: number;
  name: string;
  title: string;
  programmeCodes: string[];
}

export interface TeachingSubjectOption {
  id: number;
  code: string;
  name: string;
  studyYear: number;
  programmeCode: string;
  programmeName: string;
}

export interface MetaContext {
  user: User;
  today: string;
  todayLabel: string;
  academicYear: string;
  campusName: string;
  attendanceThreshold: number;
  programmes: ProgrammeOption[];
  years: number[];
  faculty: FacultyOption[];
  teachingSubjects: TeachingSubjectOption[];
}

export interface Dashboard {
  user: User;
  today: string;
  dayLabel: string;
  academicYear: string;
  scopeNote: string;
  metrics: {
    activeStudents: number;
    classesScheduledToday: number;
    classesSubmittedToday: number;
    attendanceThisWeek: number | null;
    belowThreshold: number;
    feeOutstanding: number;
    defaulters: number;
    openRiskFlags: number;
    mentorTasksOpen: number;
    pendingModeration: number;
  };
  classes: Array<{
    subjectId: number;
    subjectCode: string;
    subjectName: string;
    programmeCode: string;
    studyYear: number;
    period: number;
    room: string;
    facultyName: string;
    submitted: boolean;
    presentCount: number | null;
    studentCount: number | null;
    attendancePercent: number | null;
  }>;
  risks: Array<{
    flagId: number;
    studentId: number;
    studentName: string;
    rollNo: string;
    programmeCode: string;
    score: number;
    band: string;
    topReason: string;
  }>;
  dues: Array<{
    studentId: number;
    studentName: string;
    rollNo: string;
    programmeCode: string;
    dueAmount: number;
    dueDate: string;
    status: string;
  }>;
  attendanceTrend: Array<{ label: string; attendancePercent: number | null; classes: number }>;
  automation: {
    reminderCandidates: number;
    lastReminderAt: string | null;
    atRiskAlerts: number;
    alertsRefreshedAt: string | null;
  };
  pendingModeration: Array<{
    subjectId: number;
    code: string;
    name: string;
    programmeCode: string;
    moderationStatus: string;
    enteredScores: number;
    expectedScores: number;
  }>;
}

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'UNMARKED';

export interface MyClasses {
  date: string;
  dateLabel: string;
  classes: Array<{
    subjectId: number;
    subjectCode: string;
    subjectName: string;
    programmeCode: string;
    studyYear: number;
    period: number;
    room: string;
    submitted: boolean;
    submittedAt: string | null;
    presentCount: number | null;
    studentCount: number | null;
    attendancePercent: number | null;
  }>;
  subjects: Array<{
    id: number;
    code: string;
    name: string;
    studyYear: number;
    programmeCode: string;
  }>;
  attendanceThreshold: number;
}

export interface Roster {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  programmeCode: string;
  programmeName: string;
  studyYear: number;
  date: string;
  period: number;
  attendanceThreshold: number;
  session: { id: number; submittedAt: string; recordedBy: string } | null;
  students: Array<{
    id: number;
    rollNo: string;
    name: string;
    attendancePercent: number | null;
    belowThreshold: boolean;
    status: AttendanceStatus;
  }>;
  counts: { present: number; absent: number; late: number; unmarked: number };
  canEdit: boolean;
}

export interface SyncResponse {
  sessionId: number;
  date: string;
  period: number;
  submittedAt: string;
  duplicate: boolean;
  presentCount: number;
  absentCount: number;
  total: number;
  message: string;
}

export interface MarksSubjectCard {
  id: number;
  code: string;
  name: string;
  programmeCode: string;
  programmeName: string;
  studyYear: number;
  moderationStatus: string;
  enrolled: number;
  enteredScores: number;
  expectedScores: number;
  belowThresholdStudents: number;
}

export interface MarksSheet {
  subject: {
    id: number;
    code: string;
    name: string;
    programmeCode: string;
    programmeName: string;
    studyYear: number;
    credits: number;
  };
  components: Array<{ id: number; name: string; maxScore: number; sortOrder: number }>;
  state: {
    status: string;
    lockedBy: string | null;
    lockedAt: string | null;
    note: string | null;
    unlockedBy: string | null;
    unlockedAt: string | null;
    unlockReason: string | null;
  };
  rows: Array<{
    studentId: number;
    rollNo: string;
    name: string;
    attendancePercent: number | null;
    scores: Record<string, number>;
    total: number;
  }>;
  stats: {
    students: number;
    enteredScores: number;
    expectedScores: number;
    averageTotal: number;
    completionPercent: number;
    belowThresholdStudents: number;
  };
  canEdit: boolean;
  canModerate: boolean;
  audit: Array<{ id: number; actor: string; action: string; detail: string; at: string }>;
  attendanceThreshold: number;
}

export interface FeeList {
  summary: {
    totalBilled: number;
    totalCollected: number;
    totalOutstanding: number;
    defaulters: number;
    reminderCandidates: number;
    lastReminderAt: string | null;
    collectionRatePercent: number;
    academicYear: string;
  };
  rows: FeeRow[];
  recentPayments: Array<{
    paymentId: number;
    receiptNo: string;
    studentName: string;
    rollNo: string;
    amount: number;
    method: string;
    paidAt: string;
    studentId: number;
  }>;
  rowCount: number;
}

export interface FeeRow {
  accountId: number;
  studentId: number;
  rollNo: string;
  studentName: string;
  programmeCode: string;
  programmeName: string;
  studyYear: number;
  category: string;
  grossAmount: number;
  scholarshipAmount: number;
  paidAmount: number;
  dueAmount: number;
  dueDate: string;
  status: string;
  daysOverdue: number;
  lastReminderAt: string | null;
}

export interface FeeDetail {
  account: FeeRow;
  payments: Array<{
    id: number;
    receiptNo: string;
    reference: string;
    amount: number;
    method: string;
    status: string;
    paidAt: string;
    studentName: string;
    rollNo: string;
    programmeCode: string;
    academicYear: string;
    recordedBy: string;
    duplicate: boolean;
  }>;
}

export interface RiskPage {
  summary: {
    openFlags: number;
    high: number;
    medium: number;
    tasksOpen: number;
    computedAt: string | null;
    scopeNote: string;
  };
  flags: RiskItem[];
}

export interface RiskItem {
  flagId: number;
  studentId: number;
  studentName: string;
  rollNo: string;
  programmeCode: string;
  programmeName: string;
  studyYear: number;
  score: number;
  band: string;
  status: string;
  reasons: string[];
  attendancePercent: number;
  marksPercent: number;
  trendDelta: number;
  feeDue: number;
  mentorTask: MentorTask | null;
  computedAt: string;
}

export interface MentorTask {
  id: number;
  assigneeId: number;
  assigneeName: string;
  note: string;
  dueDate: string;
  status: string;
  createdBy: string;
  createdAt: string;
}

export interface AssistantAnswer {
  status: 'ANSWERED' | 'CLARIFY' | 'REFUSED';
  summary: string;
  interpretation: string;
  filters: Array<{ label: string; value: string }>;
  rowCount: number;
  columns: Array<{ key: string; label: string; type: string }>;
  rows: Array<Record<string, unknown>>;
  plan: string;
  scopeNote: string;
  examples: string[];
  clarificationOptions: string[];
  readOnly: boolean;
  generatedAt: string;
}

export interface StudentRow {
  id: number;
  rollNo: string;
  name: string;
  programmeCode: string;
  programmeName: string;
  studyYear: number;
  section: string;
  category: string;
  attendancePercent: number | null;
  internalMarks40: number | null;
  feeDue: number;
  riskBand: string | null;
  riskStatus: string | null;
  consentStatus: string;
}

export interface StudentProfile {
  id: number;
  rollNo: string;
  name: string;
  email: string;
  programmeCode: string;
  programmeName: string;
  department: string;
  studyYear: number;
  section: string;
  batch: string;
  category: string;
  gender: string;
  consentStatus: string;
  guardianName: string;
  guardianPhone: string;
  admittedOn: string;
  status: string;
  attendancePercent: number | null;
  presentCount: number;
  heldCount: number;
  internalMarks40: number | null;
  internalMarksPercent: number | null;
  attendanceBySubject: Array<{
    subjectId: number;
    code: string;
    name: string;
    present: number;
    held: number;
    percent: number | null;
    belowThreshold: boolean;
  }>;
  marksBySubject: Array<{
    subjectId: number;
    code: string;
    name: string;
    moderationStatus: string;
    components: Array<{ componentId: number; name: string; maxScore: number; score: number | null }>;
    total: number | null;
    maxTotal: number;
  }>;
  risk: {
    flagId: number;
    score: number;
    band: string;
    status: string;
    reasons: string[];
    attendancePercent: number;
    marksPercent: number;
    trendDelta: number;
    mentorTask: {
      id: number;
      assigneeName: string;
      dueDate: string;
      status: string;
      note: string;
    } | null;
  } | null;
  fee: FeeDetail | null;
  academicYear: string;
  attendanceThreshold: number;
}
