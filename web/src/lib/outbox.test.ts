import { beforeEach, describe, expect, it, vi } from 'vitest';
import { enqueue, listPending, pendingCount, removePending, type PendingAttendance } from './outbox';

const item = (clientId: string, status: PendingAttendance['records'][number]['status'] = 'PRESENT'): PendingAttendance => ({
  id: clientId,
  subjectId: 1,
  subjectLabel: 'CSE301 Data Structures',
  date: '2026-11-09',
  period: 2,
  clientId,
  records: [{ studentId: 10, status }],
  createdAt: '2026-11-09T09:00:00.000Z',
});

describe('offline attendance outbox', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('starts empty when nothing is stored', () => {
    expect(listPending()).toEqual([]);
    expect(pendingCount()).toBe(0);
  });

  it('queues an attendance sheet and survives a reload read', () => {
    enqueue(item('device-key-1'));

    expect(pendingCount()).toBe(1);
    expect(listPending()[0].subjectLabel).toContain('CSE301');
  });

  it('replaces the same device key instead of duplicating it', () => {
    enqueue(item('device-key-1', 'PRESENT'));
    enqueue(item('device-key-1', 'ABSENT'));

    expect(pendingCount()).toBe(1);
    expect(listPending()[0].records[0].status).toBe('ABSENT');
  });

  it('removes a synced sheet and notifies listeners', () => {
    const listener = vi.fn();
    window.addEventListener('campusone:outbox', listener);
    enqueue(item('device-key-1'));
    removePending('device-key-1');

    expect(pendingCount()).toBe(0);
    expect(listener).toHaveBeenCalledTimes(2);
    window.removeEventListener('campusone:outbox', listener);
  });

  it('ignores malformed local data', () => {
    localStorage.setItem('campusone.attendance.outbox.v1', '{not json');

    expect(listPending()).toEqual([]);
  });
});
