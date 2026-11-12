import type { AttendanceStatus } from './types';

export interface PendingAttendance {
  id: string;
  subjectId: number;
  subjectLabel: string;
  date: string;
  period: number;
  clientId: string;
  records: Array<{ studentId: number; status: AttendanceStatus }>;
  createdAt: string;
}

const KEY = 'campusone.attendance.outbox.v1';

export function listPending(): PendingAttendance[] {
  const raw = localStorage.getItem(KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as PendingAttendance[]) : [];
  } catch {
    return [];
  }
}

function persist(items: PendingAttendance[]) {
  localStorage.setItem(KEY, JSON.stringify(items));
  window.dispatchEvent(new Event('campusone:outbox'));
}

export function enqueue(item: PendingAttendance) {
  const existing = listPending().filter((pending) => pending.clientId !== item.clientId);
  persist([...existing, item]);
}

export function removePending(clientId: string) {
  persist(listPending().filter((pending) => pending.clientId !== clientId));
}

export function pendingCount(): number {
  return listPending().length;
}
