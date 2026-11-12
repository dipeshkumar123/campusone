import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  Loader2,
  RefreshCw,
  ShieldAlert,
  Sparkles,
  UserRoundCheck,
} from 'lucide-react';
import { api, ApiError, todayIso } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateTime, money, percent, shortDate } from '../lib/format';
import { useToast } from '../lib/toast';
import type { RiskItem } from '../lib/types';
import {
  Avatar,
  Badge,
  ErrorState,
  Field,
  Modal,
  PageHeader,
  ScoreDial,
  Spinner,
  StatCard,
} from '../components/ui';

const filters = [
  { value: 'OPEN', label: 'Open watchlist' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'ALL', label: 'All flags' },
];

export function RiskPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [filter, setFilter] = useState('OPEN');
  const [recomputing, setRecomputing] = useState(false);
  const [taskFor, setTaskFor] = useState<RiskItem | null>(null);
  const [assigneeId, setAssigneeId] = useState<number | ''>('');
  const [note, setNote] = useState('Check in about attendance and internal marks. Agree a short recovery plan with the student.');
  const [dueDate, setDueDate] = useState(addDays(todayIso(), 7));
  const [taskBusy, setTaskBusy] = useState(false);
  const [taskError, setTaskError] = useState('');
  const [completing, setCompleting] = useState<number | null>(null);

  const isAdmin = user?.role === 'ADMIN';
  const riskQuery = useQuery({ queryKey: ['risk', filter], queryFn: () => api.risk.list(filter) });
  const metaQuery = useQuery({ queryKey: ['meta'], queryFn: api.meta });

  const openTaskCount = useMemo(
    () => (riskQuery.data?.flags ?? []).filter((flag) => flag.mentorTask?.status === 'OPEN').length,
    [riskQuery.data],
  );

  if (riskQuery.isLoading) return <Spinner label="Reading the support watchlist" />;
  if (riskQuery.isError || !riskQuery.data) {
    return <ErrorState message={(riskQuery.error as Error)?.message ?? 'Risk flags could not be loaded.'} onRetry={() => riskQuery.refetch()} />;
  }

  const data = riskQuery.data;
  const faculty = metaQuery.data?.faculty ?? [];

  const recompute = async () => {
    setRecomputing(true);
    try {
      const result = await api.risk.recompute();
      notify(result.message);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['risk'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'Risk scoring could not be refreshed.', 'error');
    } finally {
      setRecomputing(false);
    }
  };

  const openTask = (flag: RiskItem) => {
    setTaskFor(flag);
    setAssigneeId(faculty[0]?.id ?? '');
    setDueDate(addDays(todayIso(), 7));
    setNote('Check in about attendance and internal marks. Agree a short recovery plan with the student.');
    setTaskError('');
  };

  const createTask = async () => {
    if (!taskFor || assigneeId === '') {
      setTaskError('Choose a faculty mentor.');
      return;
    }
    if (note.trim().length < 5) {
      setTaskError('Add a short note for the mentor.');
      return;
    }
    setTaskBusy(true);
    setTaskError('');
    try {
      await api.risk.createTask(taskFor.flagId, { assigneeId, note: note.trim(), dueDate });
      setTaskFor(null);
      notify('Mentor task created. The flag is now acknowledged.');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['risk'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
    } catch (error) {
      setTaskError(error instanceof ApiError ? error.message : 'The mentor task could not be created.');
    } finally {
      setTaskBusy(false);
    }
  };

  const completeTask = async (flag: RiskItem) => {
    if (!flag.mentorTask) return;
    setCompleting(flag.mentorTask.id);
    try {
      await api.risk.completeTask(flag.mentorTask.id);
      notify('Mentor task marked done.');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['risk'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'The task could not be completed.', 'error');
    } finally {
      setCompleting(null);
    }
  };

  return (
    <div>
      <PageHeader
        eyebrow="Student support"
        title="At-risk watchlist"
        description="Explainable, rules-based signals. A high score opens a mentor task only - no marks, fees or attendance records are changed automatically."
        actions={
          isAdmin ? (
            <button type="button" className="btn btn-primary" onClick={() => void recompute()} disabled={recomputing}>
              {recomputing ? <Loader2 size={16} className="spin" /> : <RefreshCw size={16} />}
              {recomputing ? 'Recomputing' : 'Recompute flags'}
            </button>
          ) : undefined
        }
      />

      <section className="stat-grid four">
        <StatCard label="Open flags" value={data.summary.openFlags} hint={data.summary.scopeNote} icon={<ShieldAlert size={18} />} tone={data.summary.openFlags > 0 ? 'danger' : 'good'} />
        <StatCard label="High band" value={data.summary.high} hint="Score 70 and above" icon={<ShieldAlert size={18} />} tone={data.summary.high > 0 ? 'danger' : 'good'} />
        <StatCard label="Medium band" value={data.summary.medium} hint="Score 50 to 69" icon={<Clock3 size={18} />} tone={data.summary.medium > 0 ? 'warn' : 'good'} />
        <StatCard label="Open mentor tasks" value={data.summary.tasksOpen} hint={`Last computed ${dateTime(data.summary.computedAt)}`} icon={<UserRoundCheck size={18} />} tone="brand" />
      </section>

      <div className="risk-toolbar">
        <div className="filter-tabs" role="tablist" aria-label="Risk status filter">
          {filters.map((option) => (
            <button
              key={option.value}
              type="button"
              role="tab"
              aria-selected={filter === option.value}
              className={filter === option.value ? 'active' : ''}
              onClick={() => setFilter(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
        <span className="muted small">Flags recompute from term attendance and internal marks. Low scores resolve automatically.</span>
      </div>

      <div className="risk-layout">
        <div className="risk-list">
          {data.flags.length === 0 ? (
            <div className="quiet-block card">
              <CheckCircle2 size={22} />
              <p>{filter === 'RESOLVED' ? 'No flags have been resolved yet.' : 'No students are on this watchlist right now.'}</p>
            </div>
          ) : (
            data.flags.map((flag) => (
              <article className={`risk-card risk-${flag.band.toLowerCase()}`} key={flag.flagId}>
                <div className="risk-card-head">
                  <Avatar name={flag.studentName} />
                  <div className="risk-identity">
                    <div>
                      <Link to={`/students/${flag.studentId}`}>{flag.studentName}</Link>
                      <Badge>{flag.band}</Badge>
                      {flag.status !== 'OPEN' && <Badge>{flag.status}</Badge>}
                    </div>
                    <p>{flag.rollNo} · {flag.programmeName} · Year {flag.studyYear}</p>
                  </div>
                  <ScoreDial score={flag.score} band={flag.band} />
                </div>

                <div className="risk-metrics">
                  <div>
                    <span>Attendance</span>
                    <strong className={flag.attendancePercent < 75 ? 'text-danger' : ''}>{percent(flag.attendancePercent)}</strong>
                  </div>
                  <div>
                    <span>Internal marks</span>
                    <strong className={flag.marksPercent < 60 ? 'text-danger' : ''}>{percent(flag.marksPercent)}</strong>
                  </div>
                  <div>
                    <span>Recent trend</span>
                    <strong className={flag.trendDelta < 0 ? 'text-danger' : flag.trendDelta > 0 ? 'text-good' : ''}>
                      {flag.trendDelta > 0 ? '+' : ''}{flag.trendDelta.toFixed(0)} pts
                    </strong>
                  </div>
                  <div>
                    <span>Fee due</span>
                    <strong>{flag.feeDue ? money(flag.feeDue) : 'Clear'}</strong>
                  </div>
                </div>

                <ul className="reason-list">
                  {flag.reasons.map((reason) => (
                    <li key={reason}>{reason}</li>
                  ))}
                </ul>

                <div className="risk-card-foot">
                  {flag.mentorTask ? (
                    <div className="mentor-task">
                      <span className="mentor-icon"><UserRoundCheck size={16} /></span>
                      <span>
                        <strong>{flag.mentorTask.assigneeName} · due {shortDate(flag.mentorTask.dueDate)}</strong>
                        <small>{flag.mentorTask.note}</small>
                      </span>
                      {flag.mentorTask.status === 'OPEN' && (isAdmin || flag.mentorTask.assigneeId === user?.id) && (
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => void completeTask(flag)}
                          disabled={completing === flag.mentorTask.id}
                        >
                          {completing === flag.mentorTask.id ? <Loader2 size={14} className="spin" /> : <CheckCircle2 size={14} />}
                          Mark done
                        </button>
                      )}
                      {flag.mentorTask.status === 'DONE' && <Badge tone="good">Done</Badge>}
                    </div>
                  ) : flag.status !== 'RESOLVED' ? (
                    <>
                      <span className="muted small">No mentor task yet. A task is the only automated follow-up.</span>
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => openTask(flag)}>
                        <UserRoundCheck size={14} /> Assign mentor
                      </button>
                    </>
                  ) : (
                    <span className="muted small">This flag resolved automatically when the metrics recovered.</span>
                  )}
                  <Link to={`/students/${flag.studentId}`} className="text-link risk-profile-link">
                    Open profile <ArrowRight size={14} />
                  </Link>
                </div>
              </article>
            ))
          )}
        </div>

        <aside className="risk-aside">
          <section className="card">
            <div className="card-head compact">
              <h2>Why this list?</h2>
              <Sparkles size={18} />
            </div>
            <p className="muted small">CampusOne uses a transparent points model. Every reason is shown on the card.</p>
            <div className="formula-list">
              <div className="formula-row">
                <span className="formula-weight">45</span>
                <span><strong>Attendance</strong><small>Below the 75% rule</small></span>
              </div>
              <div className="formula-row">
                <span className="formula-weight">45</span>
                <span><strong>Internal marks</strong><small>Below the 60% watch line</small></span>
              </div>
              <div className="formula-row">
                <span className="formula-weight">10</span>
                <span><strong>Recent trend</strong><small>Falling attendance in the last four classes</small></span>
              </div>
            </div>
            <div className="band-key">
              <span><span className="band-dot high" /> High 70+</span>
              <span><span className="band-dot medium" /> Medium 50-69</span>
              <span><span className="band-dot low" /> Low</span>
            </div>
          </section>

          <section className="card">
            <div className="card-head compact">
              <h2>Mentor queue</h2>
              <span className="badge badge-info">{openTaskCount} open</span>
            </div>
            {(riskQuery.data?.flags ?? []).filter((flag) => flag.mentorTask?.status === 'OPEN').length === 0 ? (
              <p className="muted small">No open mentor tasks in this view.</p>
            ) : (
              <div className="mentor-list">
                {data.flags
                  .filter((flag) => flag.mentorTask?.status === 'OPEN')
                  .slice(0, 5)
                  .map((flag) => (
                    <div className="mentor-list-row" key={flag.flagId}>
                      <Avatar name={flag.studentName} size="sm" />
                      <span>
                        <strong>{flag.studentName}</strong>
                        <small>{flag.mentorTask!.assigneeName} · {shortDate(flag.mentorTask!.dueDate)}</small>
                      </span>
                    </div>
                  ))}
              </div>
            )}
            <p className="muted small privacy-note">
              <ShieldAlert size={13} /> Support signals are visible to faculty and the office within the student’s programme scope. No external AI service receives student records.
            </p>
          </section>
        </aside>
      </div>

      <Modal
        open={taskFor !== null}
        title={taskFor ? `Assign a mentor · ${taskFor.studentName}` : 'Assign a mentor'}
        description="A task is a follow-up, not a penalty. The flag becomes acknowledged once a mentor is assigned."
        onClose={() => setTaskFor(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setTaskFor(null)}>Cancel</button>
            <button type="button" className="btn btn-primary" onClick={() => void createTask()} disabled={taskBusy}>
              {taskBusy ? <Loader2 size={15} className="spin" /> : <UserRoundCheck size={15} />}
              {taskBusy ? 'Assigning' : 'Create task'}
            </button>
          </>
        }
      >
        <div className="form-grid">
          <Field label="Mentor">
            <select value={assigneeId} onChange={(event) => setAssigneeId(event.target.value ? Number(event.target.value) : '')}>
              <option value="">Choose faculty</option>
              {faculty.map((member) => (
                <option key={member.id} value={member.id}>{member.name} · {member.title}</option>
              ))}
            </select>
          </Field>
          <Field label="Due date" hint="Within the next 60 days.">
            <input
              type="date"
              value={dueDate}
              min={todayIso()}
              max={addDays(todayIso(), 60)}
              onChange={(event) => setDueDate(event.target.value)}
            />
          </Field>
        </div>
        <Field label="Note for the mentor" hint="Be specific and supportive. This is visible on the task.">
          <textarea value={note} onChange={(event) => setNote(event.target.value)} rows={4} maxLength={500} />
        </Field>
        {taskFor && (
          <div className="modal-summary">
            <div><span>Risk score</span><strong>{taskFor.score}</strong></div>
            <div><span>Attendance</span><strong>{percent(taskFor.attendancePercent)}</strong></div>
            <div><span>Marks</span><strong>{percent(taskFor.marksPercent)}</strong></div>
          </div>
        )}
        {taskError && <div className="form-error">{taskError}</div>}
      </Modal>
    </div>
  );
}

function addDays(date: string, days: number): string {
  const parsed = new Date(`${date}T12:00:00`);
  parsed.setDate(parsed.getDate() + days);
  return parsed.toISOString().slice(0, 10);
}
