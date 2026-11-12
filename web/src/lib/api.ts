import type {
  AssistantAnswer,
  AttendanceStatus,
  Dashboard,
  FeeDetail,
  FeeList,
  LoginResponse,
  MarksSheet,
  MarksSubjectCard,
  MentorTask,
  MetaContext,
  MyClasses,
  RiskPage,
  Roster,
  StudentProfile,
  StudentRow,
  SyncResponse,
  User,
} from './types';

const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'campusone.token';

export class ApiError extends Error {
  status: number;
  code: string;
  fields?: Record<string, string>;

  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new ApiError(0, 'NETWORK', 'You appear to be offline. Your work can be saved and synced later.');
  }

  if (response.status === 401 && !path.includes('/api/auth/login')) {
    window.dispatchEvent(new Event('campusone:unauthorized'));
  }

  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const body = data as { error?: { code?: string; message?: string; fields?: Record<string, string> } } | null;
    throw new ApiError(
      response.status,
      body?.error?.code ?? 'REQUEST_FAILED',
      body?.error?.message ?? `The request failed (${response.status}).`,
      body?.error?.fields,
    );
  }

  return data as T;
}

function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') search.set(key, String(value));
  });
  const value = search.toString();
  return value ? `?${value}` : '';
}

export async function download(path: string, fallbackName: string) {
  const token = getToken();
  const response = await fetch(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!response.ok) throw new ApiError(response.status, 'DOWNLOAD_FAILED', 'The export could not be generated.');
  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = match?.[1] ?? fallbackName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export interface SyncPayload {
  subjectId: number;
  date: string;
  period: number;
  clientId: string;
  records: Array<{ studentId: number; status: AttendanceStatus }>;
}

export const api = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  me: () => request<User>('/api/auth/me'),

  meta: () => request<MetaContext>('/api/meta/context'),

  dashboard: () => request<Dashboard>('/api/dashboard'),

  students: (filters: { query?: string; programmeId?: number; year?: number } = {}) =>
    request<{ rows: StudentRow[]; total: number }>(`/api/students${query(filters)}`),

  student: (id: number) => request<StudentProfile>(`/api/students/${id}`),

  attendance: {
    classes: (date?: string) => request<MyClasses>(`/api/attendance/my-classes${query({ date })}`),
    roster: (subjectId: number, date: string, period: number) =>
      request<Roster>(`/api/attendance/roster${query({ subjectId, date, period })}`),
    sync: (payload: SyncPayload) =>
      request<SyncResponse>('/api/attendance/sync', { method: 'POST', body: JSON.stringify(payload) }),
  },

  marks: {
    subjects: () => request<MarksSubjectCard[]>('/api/marks/subjects'),
    sheet: (subjectId: number) => request<MarksSheet>(`/api/marks/${subjectId}`),
    saveDraft: (subjectId: number, entries: Array<{ studentId: number; componentId: number; score: number }>) =>
      request<MarksSheet>(`/api/marks/${subjectId}/draft`, {
        method: 'PUT',
        body: JSON.stringify({ entries }),
      }),
    lock: (subjectId: number, note?: string) =>
      request<MarksSheet>(`/api/marks/${subjectId}/lock`, {
        method: 'POST',
        body: JSON.stringify({ note: note ?? null }),
      }),
    unlock: (subjectId: number, reason: string) =>
      request<MarksSheet>(`/api/marks/${subjectId}/unlock`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
    download: (subjectId: number, code: string) => download(`/api/marks/${subjectId}/export`, `internal-marks-${code}.csv`),
  },

  fees: {
    list: (filters: { query?: string; programmeId?: number; status?: string } = {}) =>
      request<FeeList>(`/api/fees${query(filters)}`),
    detail: (studentId: number) => request<FeeDetail>(`/api/fees/${studentId}`),
    pay: (studentId: number, body: { amount: number; method: 'UPI' | 'CARD' | 'NETBANKING'; idempotencyKey: string }) =>
      request<FeeDetail['payments'][number]>(`/api/fees/${studentId}/pay`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    reminders: () =>
      request<{ count: number; queuedAt: string; messageEnglish: string; messageOdia: string }>('/api/fees/reminders', {
        method: 'POST',
      }),
    downloadTally: () => download('/api/fees/export/tally', 'tally-fee-export.csv'),
  },

  risk: {
    list: (status?: string) => request<RiskPage>(`/api/risk${query({ status })}`),
    recompute: () =>
      request<{ flagged: number; high: number; medium: number; resolved: number; tasksCreated: number; message: string }>(
        '/api/risk/recompute',
        { method: 'POST' },
      ),
    createTask: (flagId: number, body: { assigneeId: number; note: string; dueDate: string }) =>
      request<{ task: MentorTask; message: string }>(`/api/risk/flags/${flagId}/mentor-task`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    completeTask: (taskId: number) =>
      request<{ task: MentorTask; message: string }>(`/api/risk/tasks/${taskId}/complete`, { method: 'POST' }),
  },

  assistant: {
    ask: (question: string) =>
      request<AssistantAnswer>('/api/assistant/query', {
        method: 'POST',
        body: JSON.stringify({ question }),
      }),
  },
};

export function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
