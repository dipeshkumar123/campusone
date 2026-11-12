import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  CalendarCheck2,
  Check,
  CheckCircle2,
  CloudOff,
  Loader2,
  Save,
  Search,
  Users,
  WifiOff,
} from 'lucide-react';
import { api, ApiError, todayIso, type SyncPayload } from '../lib/api';
import { enqueue } from '../lib/outbox';
import { useToast } from '../lib/toast';
import { periodLabel, percent } from '../lib/format';
import type { AttendanceStatus, Roster, SyncResponse } from '../lib/types';
import { Avatar, Badge, EmptyState, ErrorState, Spinner } from '../components/ui';

const statuses: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE'];

export function AttendanceMarkPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { notify } = useToast();
  const subjectId = Number(params.get('subjectId'));
  const date = params.get('date') ?? todayIso();
  const period = Number(params.get('period') ?? 1);
  const valid = Number.isFinite(subjectId) && subjectId > 0 && Number.isFinite(period) && period >= 1 && period <= 8;

  const cacheKey = `campusone.roster.${subjectId}.${date}.${period}`;
  const query = useQuery({
    queryKey: ['roster', subjectId, date, period],
    queryFn: async () => {
      try {
        const roster = await api.attendance.roster(subjectId, date, period);
        localStorage.setItem(cacheKey, JSON.stringify(roster));
        return { roster, cached: false };
      } catch (error) {
        const raw = localStorage.getItem(cacheKey);
        if (raw) {
          return { roster: JSON.parse(raw) as Roster, cached: true };
        }
        throw error;
      }
    },
    enabled: valid,
    retry: false,
  });

  const [marks, setMarks] = useState<Record<number, AttendanceStatus>>({});
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<SyncResponse | null>(null);
  const [queued, setQueued] = useState(false);
  const [saveError, setSaveError] = useState('');

  const roster = query.data?.roster;

  useEffect(() => {
    if (!roster) return;
    const initial: Record<number, AttendanceStatus> = {};
    roster.students.forEach((student) => {
      initial[student.id] =
        student.status === 'UNMARKED' ? 'PRESENT' : student.status;
    });
    setMarks(initial);
  }, [roster]);

  useEffect(() => {
    setResult(null);
    setQueued(false);
    setSearch('');
  }, [subjectId, date, period]);

  const filtered = useMemo(() => {
    if (!roster) return [];
    const needle = search.trim().toLowerCase();
    if (!needle) return roster.students;
    return roster.students.filter(
      (student) => student.name.toLowerCase().includes(needle) || student.rollNo.toLowerCase().includes(needle),
    );
  }, [roster, search]);

  const counts = useMemo(() => {
    const values = Object.values(marks);
    return {
      present: values.filter((value) => value === 'PRESENT').length,
      absent: values.filter((value) => value === 'ABSENT').length,
      late: values.filter((value) => value === 'LATE').length,
    };
  }, [marks]);

  if (!valid) {
    return (
      <div>
        <Link to="/attendance" className="back-link"><ArrowLeft size={15} /> Attendance</Link>
        <EmptyState title="Class not found" description="The roster link is missing a subject, date or period." />
      </div>
    );
  }

  if (query.isLoading) return <Spinner label="Opening the class roster" />;
  if (query.isError || !roster) {
    return (
      <div>
        <Link to="/attendance" className="back-link"><ArrowLeft size={15} /> Attendance</Link>
        <ErrorState
          message={
            query.error instanceof ApiError
              ? query.error.message
              : 'This roster is not available offline yet. Open it once while connected.'
          }
          onRetry={() => query.refetch()}
        />
      </div>
    );
  }

  const setAll = (status: AttendanceStatus) => {
    const next: Record<number, AttendanceStatus> = {};
    roster.students.forEach((student) => {
      next[student.id] = status;
    });
    setMarks(next);
    setResult(null);
    setQueued(false);
  };

  const submit = async () => {
    setSaving(true);
    setSaveError('');
    const records = roster.students.map((student) => ({
      studentId: student.id,
      status: marks[student.id] ?? 'PRESENT',
    }));
    const payload: SyncPayload = {
      subjectId: roster.subjectId,
      date: roster.date,
      period: roster.period,
      clientId: crypto.randomUUID(),
      records,
    };

    try {
      if (!navigator.onLine) throw new ApiError(0, 'NETWORK', 'Offline');
      const response = await api.attendance.sync(payload);
      setResult(response);
      setQueued(false);
      notify(response.duplicate ? 'This class was already synced from this device.' : 'Attendance submitted.');
      await query.refetch();
    } catch (error) {
      if (error instanceof ApiError && error.code === 'NETWORK') {
        enqueue({
          id: crypto.randomUUID(),
          subjectId: roster.subjectId,
          subjectLabel: `${roster.subjectCode} · ${roster.subjectName}`,
          date: roster.date,
          period: roster.period,
          clientId: payload.clientId,
          records,
          createdAt: new Date().toISOString(),
        });
        setQueued(true);
        notify('Saved offline. It will sync when this device reconnects.', 'info');
      } else {
        setSaveError(error instanceof ApiError ? error.message : 'Attendance could not be saved.');
      }
    } finally {
      setSaving(false);
    }
  };

  const sessionExists = Boolean(roster.session);
  const offline = !navigator.onLine || Boolean(query.data?.cached);

  return (
    <div className="mark-page">
      <Link to="/attendance" className="back-link"><ArrowLeft size={15} /> Attendance desk</Link>

      <header className="mark-header">
        <div>
          <div className="mark-title-row">
            <p className="eyebrow">{roster.programmeCode} · Year {roster.studyYear} · {periodLabel(roster.period)}</p>
            {sessionExists ? <Badge tone="good">Submitted</Badge> : <Badge tone="warn">Not submitted</Badge>}
          </div>
          <h1>{roster.subjectName}</h1>
          <p className="page-description">
            {roster.subjectCode} · {roster.programmeName} · {new Date(`${roster.date}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}
          </p>
        </div>
        <div className="mark-meta">
          {sessionExists && (
            <span><CalendarCheck2 size={15} /> Last saved {new Date(roster.session!.submittedAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })}</span>
          )}
          <span><Users size={15} /> {roster.students.length} students</span>
        </div>
      </header>

      {(offline || query.data?.cached) && (
        <div className="inline-banner warn">
          <WifiOff size={17} />
          <span>
            <strong>Working from this device.</strong> Live sync is unavailable. Submitting will keep the class in the offline queue.
          </span>
        </div>
      )}

      {result && (
        <div className="inline-banner success">
          <CheckCircle2 size={18} />
          <span>
            <strong>{result.message}</strong> {result.presentCount} present, {result.absentCount} absent across {result.total} students.
          </span>
        </div>
      )}

      {queued && (
        <div className="inline-banner info">
          <CloudOff size={18} />
          <span>
            <strong>Saved on this device.</strong> This class will sync with a unique key, so a retry cannot create a duplicate.
          </span>
        </div>
      )}

      {saveError && (
        <div className="inline-banner error">
          <AlertTriangle size={17} />
          <span><strong>Could not save.</strong> {saveError}</span>
        </div>
      )}

      <section className="card roster-card">
        <div className="roster-toolbar">
          <div className="search-field">
            <Search size={16} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Find a student or roll number"
              aria-label="Search roster"
            />
          </div>
          <div className="bulk-actions">
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAll('PRESENT')}>
              <Check size={14} /> All present
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setAll('ABSENT')}>
              All absent
            </button>
          </div>
        </div>

        <div className="roster-head">
          <span>Student</span>
          <span>Term attendance</span>
          <span>Today</span>
        </div>

        {filtered.length === 0 ? (
          <EmptyState title="No matching students" description="Try a different name or roll number." />
        ) : (
          <div className="roster-list">
            {filtered.map((student) => {
              const status = marks[student.id] ?? 'PRESENT';
              return (
                <div className={`roster-row status-${status.toLowerCase()}`} key={student.id}>
                  <div className="roster-student">
                    <Avatar name={student.name} size="sm" />
                    <span>
                      <strong>{student.name}</strong>
                      <small>{student.rollNo}</small>
                    </span>
                  </div>
                  <div className="roster-attendance">
                    <span className={student.belowThreshold ? 'below' : ''}>{percent(student.attendancePercent)}</span>
                    {student.belowThreshold && <small><AlertTriangle size={12} /> below {roster.attendanceThreshold}%</small>}
                  </div>
                  <div className="attendance-seg" role="group" aria-label={`Attendance for ${student.name}`}>
                    {statuses.map((value) => (
                      <button
                        key={value}
                        type="button"
                        className={`seg-${value.toLowerCase()} ${status === value ? 'active' : ''}`}
                        aria-pressed={status === value}
                        onClick={() => {
                          setMarks((current) => ({ ...current, [student.id]: value }));
                          setResult(null);
                          setQueued(false);
                        }}
                      >
                        {value === 'PRESENT' ? 'P' : value === 'ABSENT' ? 'A' : 'L'}
                        <span className="seg-label">{value === 'PRESENT' ? 'Present' : value === 'ABSENT' ? 'Absent' : 'Late'}</span>
                      </button>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      <div className="submit-bar">
        <div className="submit-counts">
          <span className="count-present"><strong>{counts.present}</strong> present</span>
          <span className="count-late"><strong>{counts.late}</strong> late</span>
          <span className="count-absent"><strong>{counts.absent}</strong> absent</span>
          <span className="muted small">of {roster.students.length}</span>
        </div>
        <div className="submit-actions">
          <button type="button" className="btn btn-secondary" onClick={() => navigate('/attendance')}>Cancel</button>
          <button type="button" className="btn btn-primary" onClick={() => void submit()} disabled={saving}>
            {saving ? <Loader2 size={16} className="spin" /> : <Save size={16} />}
            {saving ? 'Saving' : sessionExists ? 'Update attendance' : 'Submit attendance'}
          </button>
        </div>
      </div>
    </div>
  );
}
