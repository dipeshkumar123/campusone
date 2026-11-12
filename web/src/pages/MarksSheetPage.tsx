import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Download,
  History,
  Loader2,
  LockKeyhole,
  Save,
  Search,
  UnlockKeyhole,
} from 'lucide-react';
import { api, ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { dateTime, percent, titleCase } from '../lib/format';
import { useToast } from '../lib/toast';
import type { MarksSheet } from '../lib/types';
import { Avatar, Badge, ErrorState, Field, Modal, PageHeader, Spinner, StatCard } from '../components/ui';

export function MarksSheetPage() {
  const { subjectId } = useParams();
  const id = Number(subjectId);
  const { user } = useAuth();
  const { notify } = useToast();
  const query = useQuery({
    queryKey: ['marks-sheet', id],
    queryFn: () => api.marks.sheet(id),
    enabled: Number.isFinite(id) && id > 0,
    refetchOnWindowFocus: false,
  });
  const sheet = query.data;

  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [search, setSearch] = useState('');
  const [saving, setSaving] = useState(false);
  const [modal, setModal] = useState<'lock' | 'unlock' | null>(null);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');
  const [modalBusy, setModalBusy] = useState(false);
  const [modalError, setModalError] = useState('');

  useEffect(() => {
    if (!sheet) return;
    const next: Record<string, string> = {};
    sheet.rows.forEach((row) => {
      sheet.components.forEach((component) => {
        const value = row.scores[String(component.id)];
        next[`${row.studentId}:${component.id}`] = value === undefined || value === null ? '' : String(value);
      });
    });
    setDrafts(next);
  }, [sheet]);

  const filteredRows = useMemo(() => {
    if (!sheet) return [];
    const needle = search.trim().toLowerCase();
    if (!needle) return sheet.rows;
    return sheet.rows.filter(
      (row) => row.name.toLowerCase().includes(needle) || row.rollNo.toLowerCase().includes(needle),
    );
  }, [sheet, search]);

  const unsavedCount = useMemo(() => {
    if (!sheet) return 0;
    return sheet.rows.reduce((count, row) => {
      return (
        count +
        sheet.components.filter((component) => {
          const serverValue = row.scores[String(component.id)];
          const draftValue = drafts[`${row.studentId}:${component.id}`] ?? '';
          return normalize(draftValue) !== normalize(serverValue === null || serverValue === undefined ? '' : String(serverValue));
        }).length
      );
    }, 0);
  }, [sheet, drafts]);

  const maxTotal = sheet?.components.reduce((sum, component) => sum + component.maxScore, 0) ?? 0;
  const locked = sheet?.state.status === 'LOCKED';
  const canEdit = Boolean(sheet?.canEdit && !locked);
  const isAdmin = user?.role === 'ADMIN';

  if (query.isLoading) return <Spinner label="Opening the marks sheet" />;
  if (query.isError || !sheet) {
    return (
      <div>
        <Link to="/marks" className="back-link"><ArrowLeft size={15} /> Internal marks</Link>
        <ErrorState message={(query.error as Error)?.message ?? 'The marks sheet could not be loaded.'} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const saveDraft = async () => {
    const entries: Array<{ studentId: number; componentId: number; score: number }> = [];
    const invalid: string[] = [];
    for (const row of sheet.rows) {
      for (const component of sheet.components) {
        const raw = (drafts[`${row.studentId}:${component.id}`] ?? '').trim();
        if (raw === '') continue;
        const value = Number(raw);
        if (!Number.isFinite(value) || value < 0 || value > component.maxScore) {
          invalid.push(`${row.rollNo} · ${component.name} (0-${component.maxScore})`);
        } else {
          entries.push({ studentId: row.studentId, componentId: component.id, score: value });
        }
      }
    }
    if (invalid.length > 0) {
      notify(`Check ${invalid.slice(0, 2).join(', ')}${invalid.length > 2 ? ` and ${invalid.length - 2} more` : ''}.`, 'error');
      return;
    }
    if (entries.length === 0) {
      notify('Enter at least one mark before saving.', 'info');
      return;
    }

    setSaving(true);
    try {
      await api.marks.saveDraft(sheet.subject.id, entries);
      await query.refetch();
      notify('Draft marks saved.');
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'Marks could not be saved.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const openModal = (type: 'lock' | 'unlock') => {
    setModal(type);
    setModalError('');
    setNote('');
    setReason('');
  };

  const moderate = async () => {
    setModalBusy(true);
    setModalError('');
    try {
      if (modal === 'lock') {
        await api.marks.lock(sheet.subject.id, note.trim() || undefined);
        notify('Marks locked after moderation.');
      } else if (modal === 'unlock') {
        await api.marks.unlock(sheet.subject.id, reason.trim());
        notify('Marks unlocked. The reason is in the audit trail.');
      }
      setModal(null);
      await query.refetch();
    } catch (error) {
      setModalError(error instanceof ApiError ? error.message : 'The moderation action failed.');
    } finally {
      setModalBusy(false);
    }
  };

  const rowTotal = (row: MarksSheet['rows'][number]) =>
    sheet.components.reduce((sum, component) => {
      const value = Number(drafts[`${row.studentId}:${component.id}`] ?? '');
      return sum + (Number.isFinite(value) ? value : 0);
    }, 0);

  const invalidCell = (value: string, max: number) => {
    if (!value.trim()) return false;
    const parsed = Number(value);
    return !Number.isFinite(parsed) || parsed < 0 || parsed > max;
  };

  return (
    <div>
      <Link to="/marks" className="back-link"><ArrowLeft size={15} /> Internal marks</Link>

      <PageHeader
        eyebrow={`${sheet.subject.programmeCode} · Year ${sheet.subject.studyYear} · ${sheet.subject.credits} credits`}
        title={sheet.subject.name}
        description={`${sheet.subject.code} · ${sheet.subject.programmeName}`}
        actions={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => void api.marks.download(sheet.subject.id, sheet.subject.code)}>
              <Download size={16} /> Export CSV
            </button>
            {isAdmin && !locked && (
              <button
                type="button"
                className="btn btn-primary"
                disabled={sheet.stats.completionPercent < 100}
                title={sheet.stats.completionPercent < 100 ? 'Every score must be entered before moderation can lock the sheet.' : undefined}
                onClick={() => openModal('lock')}
              >
                <LockKeyhole size={16} /> Lock moderation
              </button>
            )}
            {isAdmin && locked && (
              <button type="button" className="btn btn-primary" onClick={() => openModal('unlock')}>
                <UnlockKeyhole size={16} /> Unlock sheet
              </button>
            )}
          </>
        }
      />

      {locked && (
        <div className="inline-banner success">
          <LockKeyhole size={18} />
          <span>
            <strong>Locked after moderation.</strong> {sheet.state.lockedBy ? `Locked by ${sheet.state.lockedBy}` : ''}
            {sheet.state.lockedAt ? ` on ${dateTime(sheet.state.lockedAt)}` : ''}.
            {sheet.state.note ? ` Note: ${sheet.state.note}` : ''}
            {sheet.state.unlockReason ? ` Previously unlocked: ${sheet.state.unlockReason}` : ''}
          </span>
        </div>
      )}

      <section className="stat-grid four">
        <StatCard label="Students" value={sheet.stats.students} hint={`${sheet.components.length} assessment components`} />
        <StatCard
          label="Completion"
          value={percent(sheet.stats.completionPercent, 0)}
          hint={`${sheet.stats.enteredScores} of ${sheet.stats.expectedScores} scores`}
          tone={sheet.stats.completionPercent === 100 ? 'good' : 'warn'}
        />
        <StatCard label="Average total" value={`${sheet.stats.averageTotal.toFixed(1)}/${maxTotal}`} hint="Submitted scores only" tone="brand" />
        <StatCard
          label="Below attendance line"
          value={sheet.stats.belowThresholdStudents}
          hint={`Under ${sheet.attendanceThreshold}%`}
          tone={sheet.stats.belowThresholdStudents > 0 ? 'danger' : 'good'}
        />
      </section>

      <section className="card sheet-card">
        <div className="sheet-toolbar">
          <div className="search-field">
            <Search size={16} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Find a student or roll number"
              aria-label="Search marks sheet"
            />
          </div>
          <div className="sheet-toolbar-right">
            <span className="muted small">
              {unsavedCount > 0 ? `${unsavedCount} unsaved ${unsavedCount === 1 ? 'change' : 'changes'}` : 'All changes saved'}
            </span>
            {canEdit && (
              <button type="button" className="btn btn-primary btn-sm" onClick={() => void saveDraft()} disabled={saving || unsavedCount === 0}>
                {saving ? <Loader2 size={15} className="spin" /> : <Save size={15} />}
                {saving ? 'Saving' : 'Save draft'}
              </button>
            )}
          </div>
        </div>

        <div className="table-wrap">
          <table className="marks-table">
            <thead>
              <tr>
                <th className="sticky-col">Student</th>
                <th className="number-col">Attendance</th>
                {sheet.components.map((component) => (
                  <th key={component.id} className="number-col">
                    {component.name}
                    <small>/{component.maxScore}</small>
                  </th>
                ))}
                <th className="number-col total-col">Total<small>/{maxTotal}</small></th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => (
                <tr key={row.studentId}>
                  <td className="sticky-col">
                    <div className="table-student">
                      <Avatar name={row.name} size="sm" />
                      <span>
                        <Link to={`/students/${row.studentId}`}>{row.name}</Link>
                        <small>{row.rollNo}</small>
                      </span>
                    </div>
                  </td>
                  <td className="number-col">
                    <span className={(row.attendancePercent ?? 100) < sheet.attendanceThreshold ? 'text-danger' : 'muted'}>
                      {percent(row.attendancePercent)}
                    </span>
                  </td>
                  {sheet.components.map((component) => {
                    const key = `${row.studentId}:${component.id}`;
                    const invalid = invalidCell(drafts[key] ?? '', component.maxScore);
                    return (
                      <td key={component.id} className="number-col">
                        <input
                          className={`score-input ${invalid ? 'invalid' : ''}`}
                          inputMode="decimal"
                          value={drafts[key] ?? ''}
                          disabled={!canEdit}
                          aria-label={`${row.name} ${component.name}`}
                          onChange={(event) => {
                            const value = event.target.value;
                            if (value !== '' && !/^\d*\.?\d{0,2}$/.test(value)) return;
                            setDrafts((current) => ({ ...current, [key]: value }));
                          }}
                          placeholder="-"
                        />
                      </td>
                    );
                  })}
                  <td className="number-col total-col">
                    <strong>{rowTotal(row).toFixed(rowTotal(row) % 1 === 0 ? 0 : 1)}</strong>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="sheet-foot">
          <span className="muted small">
            <CheckCircle2 size={14} /> {unsavedCount === 0 ? 'Draft is in sync with the server' : 'Unsaved changes stay on this screen until you save'}
          </span>
          {!canEdit && !locked && <span className="muted small">Office admin view · faculty enter scores</span>}
          {locked && <span className="muted small"><LockKeyhole size={14} /> Locked sheets are read-only</span>}
        </div>
      </section>

      <div className="sheet-lower">
        <section className="card">
          <div className="card-head compact">
            <div>
              <h2>Sheet history</h2>
              <p className="muted small">Every mark edit and moderation action is attributable.</p>
            </div>
            <History size={18} />
          </div>
          {sheet.audit.length === 0 ? (
            <p className="muted small">No audit entries yet.</p>
          ) : (
            <div className="audit-list">
              {sheet.audit.slice(0, 8).map((entry) => (
                <div className="audit-row" key={entry.id}>
                  <span className="audit-dot" />
                  <span>
                    <strong>{titleCase(entry.action)}</strong>
                    <small>{entry.actor} · {dateTime(entry.at)}</small>
                    <p>{entry.detail}</p>
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card">
          <div className="card-head compact">
            <h2>Moderation state</h2>
            <Badge>{sheet.state.status}</Badge>
          </div>
          <div className="quick-read-row">
            <span>Entry owner</span>
            <strong>{sheet.canEdit ? 'You' : 'Faculty'}</strong>
          </div>
          <div className="quick-read-row">
            <span>Locked by</span>
            <strong>{sheet.state.lockedBy ?? '—'}</strong>
          </div>
          <div className="quick-read-row">
            <span>Last unlocked</span>
            <strong>{sheet.state.unlockedAt ? dateTime(sheet.state.unlockedAt) : '—'}</strong>
          </div>
          {sheet.state.unlockReason && (
            <div className="note-block">
              <AlertTriangle size={15} />
              <p>{sheet.state.unlockReason}</p>
            </div>
          )}
          {!locked && sheet.stats.completionPercent < 100 && (
            <div className="note-block warn">
              <AlertTriangle size={15} />
              <p>Complete all {sheet.stats.expectedScores} scores before locking moderation.</p>
            </div>
          )}
          {!locked && sheet.stats.completionPercent === 100 && sheet.canModerate && (
            <div className="note-block good">
              <CheckCircle2 size={15} />
              <p>All scores are in. This sheet is ready for moderation.</p>
            </div>
          )}
        </section>
      </div>

      <Modal
        open={modal !== null}
        title={modal === 'unlock' ? 'Unlock this marks sheet' : 'Lock marks after moderation'}
        description={
          modal === 'unlock'
            ? 'Unlocking re-opens score entry. The reason is stored in the audit trail.'
            : 'Locking makes the sheet read-only for faculty and freezes the moderated totals.'
        }
        onClose={() => setModal(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setModal(null)}>Cancel</button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => void moderate()}
              disabled={modalBusy || (modal === 'unlock' && reason.trim().length < 5)}
            >
              {modalBusy ? <Loader2 size={15} className="spin" /> : modal === 'unlock' ? <UnlockKeyhole size={15} /> : <LockKeyhole size={15} />}
              {modal === 'unlock' ? 'Unlock sheet' : 'Lock sheet'}
            </button>
          </>
        }
      >
        {modal === 'lock' ? (
          <Field label="Moderation note (optional)" hint="For example: department moderation complete; totals verified.">
            <textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} maxLength={500} placeholder="Add context for the audit trail" />
          </Field>
        ) : (
          <Field label="Reason for unlocking" hint="At least 5 characters. This is visible in the audit trail." error={modalError}>
            <textarea value={reason} onChange={(event) => setReason(event.target.value)} rows={3} maxLength={500} placeholder="For example: correction approved by the exam cell" />
          </Field>
        )}
        {modal === 'lock' && modalError && <div className="form-error">{modalError}</div>}
        {modal === 'lock' && (
          <div className="modal-summary">
            <div><span>Students</span><strong>{sheet.stats.students}</strong></div>
            <div><span>Scores entered</span><strong>{sheet.stats.enteredScores}/{sheet.stats.expectedScores}</strong></div>
            <div><span>Average total</span><strong>{sheet.stats.averageTotal.toFixed(1)}</strong></div>
          </div>
        )}
      </Modal>
    </div>
  );
}

function normalize(value: string): string {
  const parsed = Number(value);
  if (value.trim() === '' || !Number.isFinite(parsed)) return '';
  return String(parsed);
}
