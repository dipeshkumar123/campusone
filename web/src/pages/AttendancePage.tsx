import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CalendarCheck2,
  CheckCircle2,
  CloudUpload,
  Clock3,
  MapPin,
  RefreshCw,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { api, ApiError, todayIso } from '../lib/api';
import { periodLabel } from '../lib/format';
import { listPending, removePending, type PendingAttendance } from '../lib/outbox';
import { useToast } from '../lib/toast';
import { ErrorState, Field, Modal, PageHeader, Spinner } from '../components/ui';

export function AttendancePage() {
  const today = todayIso();
  const [date, setDate] = useState(today);
  const [online, setOnline] = useState(navigator.onLine);
  const [pending, setPending] = useState<PendingAttendance[]>(() => listPending());
  const [flushing, setFlushing] = useState(false);
  const flushRef = useRef(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [subjectId, setSubjectId] = useState<number | ''>('');
  const [period, setPeriod] = useState(1);
  const { notify } = useToast();

  const query = useQuery({
    queryKey: ['attendance-classes', date],
    queryFn: () => api.attendance.classes(date),
  });

  useEffect(() => {
    const refresh = () => setPending(listPending());
    const onConnection = () => setOnline(navigator.onLine);
    window.addEventListener('campusone:outbox', refresh);
    window.addEventListener('online', onConnection);
    window.addEventListener('offline', onConnection);
    return () => {
      window.removeEventListener('campusone:outbox', refresh);
      window.removeEventListener('online', onConnection);
      window.removeEventListener('offline', onConnection);
    };
  }, []);

  const flush = useCallback(async () => {
    const items = listPending();
    if (items.length === 0 || flushRef.current) return;
    if (!navigator.onLine) {
      notify('You are offline. Queued attendance will sync when you reconnect.', 'info');
      return;
    }
    flushRef.current = true;
    setFlushing(true);
    let synced = 0;
    try {
      for (const item of items) {
        try {
          await api.attendance.sync({
            subjectId: item.subjectId,
            date: item.date,
            period: item.period,
            clientId: item.clientId,
            records: item.records,
          });
          removePending(item.clientId);
          synced += 1;
        } catch (error) {
          if (error instanceof ApiError && error.status > 0) {
            notify(`${item.subjectLabel} could not sync: ${error.message}`, 'error');
            break;
          }
          notify('Still offline. We will keep the class in the queue.', 'info');
          break;
        }
      }
    } finally {
      flushRef.current = false;
      setFlushing(false);
      setPending(listPending());
    }
    if (synced > 0) {
      notify(`${synced} queued ${synced === 1 ? 'class' : 'classes'} synced safely.`);
      void query.refetch();
    }
  }, [notify, query]);

  useEffect(() => {
    if (navigator.onLine && pending.length > 0) void flush();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selectedSubject = query.data?.subjects.find((subject) => subject.id === subjectId);

  return (
    <div>
      <PageHeader
        eyebrow="Teaching workspace"
        title="Attendance"
        description="Mark a class in under a minute. If the network drops, the roster stays on this device and syncs later."
        actions={
          <div className="date-control">
            <CalendarCheck2 size={16} />
            <input
              type="date"
              value={date}
              max={today}
              min={shiftDays(today, -14)}
              onChange={(event) => setDate(event.target.value)}
              aria-label="Class date"
            />
          </div>
        }
      />

      <div className="attendance-status">
        <span className={`connection-chip ${online ? 'is-online' : 'is-offline'}`}>
          {online ? <Wifi size={14} /> : <WifiOff size={14} />}
          {online ? 'Online · recording to campus' : 'Offline · saved on this device'}
        </span>
        {pending.length > 0 && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => void flush()} disabled={flushing}>
            <RefreshCw size={14} className={flushing ? 'spin' : ''} />
            {flushing ? 'Syncing' : `Sync ${pending.length} queued`}
          </button>
        )}
        <span className="muted small">Corrections allowed for 14 days. Future classes cannot be marked.</span>
      </div>

      {pending.length > 0 && (
        <section className="card queue-card">
          <div className="card-head">
            <div>
              <h2>Waiting to sync</h2>
              <p className="muted small">Each class carries a unique sync key, so retries cannot create duplicates.</p>
            </div>
            <CloudUpload size={20} />
          </div>
          <div className="queue-list">
            {pending.map((item) => (
              <div className="queue-row" key={item.clientId}>
                <span className="queue-icon"><CloudUpload size={16} /></span>
                <span>
                  <strong>{item.subjectLabel}</strong>
                  <small>{item.date} · period {item.period} · {item.records.length} students</small>
                </span>
                <span className="badge badge-warn">Queued</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {query.isLoading ? (
        <Spinner label="Loading your timetable" />
      ) : query.isError ? (
        <ErrorState message={(query.error as Error).message} onRetry={() => query.refetch()} />
      ) : (
        <>
          <section className="card">
            <div className="card-head">
              <div>
                <h2>{query.data!.dateLabel} classes</h2>
                <p className="muted small">
                  {query.data!.classes.length === 0
                    ? 'No scheduled classes on this date.'
                    : `${query.data!.classes.filter((item) => item.submitted).length} of ${query.data!.classes.length} already submitted`}
                </p>
              </div>
              <span className="threshold-note">Threshold {query.data!.attendanceThreshold}%</span>
            </div>

            {query.data!.classes.length === 0 ? (
              <div className="quiet-block">
                <Clock3 size={20} />
                <p>No teaching slots fall on this date. You can still open a subject below for a correction.</p>
              </div>
            ) : (
              <div className="today-grid">
                {query.data!.classes.map((item) => (
                  <article className={`class-card ${item.submitted ? 'is-submitted' : ''}`} key={`${item.subjectId}-${item.period}`}>
                    <div className="class-card-top">
                      <span className="subject-code">{item.subjectCode}</span>
                      {item.submitted ? (
                        <span className="class-status good"><CheckCircle2 size={14} /> Submitted</span>
                      ) : (
                        <span className="class-status waiting">Not marked</span>
                      )}
                    </div>
                    <h3>{item.subjectName}</h3>
                    <p className="muted small">{item.programmeCode} · Year {item.studyYear}</p>
                    <div className="class-meta">
                      <span><Clock3 size={14} /> {periodLabel(item.period)}</span>
                      <span><MapPin size={14} /> {item.room}</span>
                    </div>
                    {item.submitted && (
                      <div className="submitted-summary">
                        <strong>{item.presentCount}/{item.studentCount}</strong> present
                        <span>{item.attendancePercent?.toFixed(1)}%</span>
                      </div>
                    )}
                    <Link
                      to={`/attendance/mark?subjectId=${item.subjectId}&date=${query.data!.date}&period=${item.period}`}
                      className={`btn ${item.submitted ? 'btn-secondary' : 'btn-primary'} btn-block`}
                    >
                      {item.submitted ? 'Review or correct' : 'Take attendance'}
                      <ArrowRight size={15} />
                    </Link>
                  </article>
                ))}
              </div>
            )}
          </section>

          <div className="attendance-lower">
            <section className="card">
              <div className="card-head">
                <div>
                  <h2>All teaching subjects</h2>
                  <p className="muted small">Open any assigned subject for a backdated correction.</p>
                </div>
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setPickerOpen(true)}>
                  Open a class
                </button>
              </div>
              <div className="subject-list">
                {query.data!.subjects.map((subject) => (
                  <div className="subject-row" key={subject.id}>
                    <span className="subject-code">{subject.code}</span>
                    <span className="class-copy">
                      <strong>{subject.name}</strong>
                      <small>{subject.programmeCode} · Year {subject.studyYear}</small>
                    </span>
                    <button
                      type="button"
                      className="text-link"
                      onClick={() => {
                        setSubjectId(subject.id);
                        setPeriod(1);
                        setPickerOpen(true);
                      }}
                    >
                      Open <ArrowRight size={14} />
                    </button>
                  </div>
                ))}
              </div>
            </section>

            <section className="card offline-explainer">
              <div className="card-head compact">
                <h2>How offline mode works</h2>
              </div>
              <ol className="steps">
                <li><strong>Mark normally.</strong> The roster is cached on the device when you open it.</li>
                <li><strong>Lose signal? Keep going.</strong> Submit stores the class in a local queue.</li>
                <li><strong>Reconnect.</strong> The queue syncs with a unique client key; the server rejects duplicates.</li>
              </ol>
              <p className="muted small">Cached rosters are scoped to this browser. Signing out clears local draft data.</p>
            </section>
          </div>
        </>
      )}

      <Modal
        open={pickerOpen}
        title="Open a class"
        description="Choose a subject and period. The date comes from the picker at the top of this page."
        onClose={() => setPickerOpen(false)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setPickerOpen(false)}>Cancel</button>
            {selectedSubject && (
              <Link
                className="btn btn-primary"
                to={`/attendance/mark?subjectId=${selectedSubject.id}&date=${date}&period=${period}`}
              >
                Open roster <ArrowRight size={15} />
              </Link>
            )}
          </>
        }
      >
        <div className="form-grid">
          <Field label="Subject">
            <select value={subjectId} onChange={(event) => setSubjectId(event.target.value ? Number(event.target.value) : '')}>
              <option value="">Choose a subject</option>
              {query.data?.subjects.map((subject) => (
                <option value={subject.id} key={subject.id}>{subject.code} · {subject.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Period" hint="Periods run from 9:00 to 5:20.">
            <select value={period} onChange={(event) => setPeriod(Number(event.target.value))}>
              {[1, 2, 3, 4, 5, 6, 7, 8].map((value) => (
                <option value={value} key={value}>{periodLabel(value)}</option>
              ))}
            </select>
          </Field>
        </div>
      </Modal>
    </div>
  );
}

function shiftDays(date: string, days: number): string {
  const parsed = new Date(`${date}T12:00:00`);
  parsed.setDate(parsed.getDate() + days);
  return parsed.toISOString().slice(0, 10);
}
