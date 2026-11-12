import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  CalendarCheck2,
  ClipboardList,
  GraduationCap,
  Mail,
  Phone,
  ShieldAlert,
  UserRoundCheck,
  Wallet,
} from 'lucide-react';
import { api } from '../lib/api';
import { dateTime, longDate, money, percent, shortDate } from '../lib/format';
import { Avatar, Badge, ErrorState, ProgressBar, ScoreDial, Spinner, StatCard } from '../components/ui';

export function StudentProfilePage() {
  const { studentId } = useParams();
  const id = Number(studentId);
  const query = useQuery({
    queryKey: ['student', id],
    queryFn: () => api.student(id),
    enabled: Number.isFinite(id) && id > 0,
  });

  if (query.isLoading) return <Spinner label="Opening student 360" />;
  if (query.isError || !query.data) {
    return (
      <div>
        <Link to="/students" className="back-link"><ArrowLeft size={15} /> Students</Link>
        <ErrorState message={(query.error as Error)?.message ?? 'The student profile could not be loaded.'} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const profile = query.data;
  const risk = profile.risk;

  return (
    <div>
      <Link to="/students" className="back-link"><ArrowLeft size={15} /> Student directory</Link>

      <section className="profile-hero">
        <Avatar name={profile.name} size="lg" />
        <div className="profile-identity">
          <div className="mark-title-row">
            <p className="eyebrow">{profile.rollNo}</p>
            <Badge>{profile.status}</Badge>
            <Badge>{profile.consentStatus}</Badge>
          </div>
          <h1>{profile.name}</h1>
          <p className="muted">{profile.programmeName} · Year {profile.studyYear}, Section {profile.section} · Batch {profile.batch}</p>
          <div className="profile-contacts">
            <span><Mail size={14} /> {profile.email}</span>
            <span><Phone size={14} /> {profile.guardianPhone}</span>
          </div>
        </div>
        <div className="profile-hero-actions">
          {risk && (
            <Link to="/risk" className="btn btn-secondary">
              <ShieldAlert size={16} /> Support view
            </Link>
          )}
          <Link to="/students" className="btn btn-ghost">All students</Link>
        </div>
      </section>

      <section className="stat-grid four">
        <StatCard
          label="Term attendance"
          value={percent(profile.attendancePercent)}
          hint={`${profile.presentCount} present of ${profile.heldCount} classes`}
          icon={<CalendarCheck2 size={18} />}
          tone={(profile.attendancePercent ?? 100) < profile.attendanceThreshold ? 'danger' : 'good'}
        />
        <StatCard
          label="Internal marks"
          value={`${profile.internalMarks40?.toFixed(1) ?? '—'}/40`}
          hint={`${percent(profile.internalMarksPercent)} of internal assessment`}
          icon={<ClipboardList size={18} />}
          tone="brand"
        />
        <StatCard
          label="Fee position"
          value={profile.fee ? (profile.fee.account.dueAmount > 0 ? money(profile.fee.account.dueAmount) : 'Cleared') : 'Restricted'}
          hint={profile.fee ? `${money(profile.fee.account.paidAmount)} paid this year` : 'Visible to the office only'}
          icon={<Wallet size={18} />}
          tone={profile.fee && profile.fee.account.dueAmount > 0 ? 'warn' : 'good'}
        />
        <StatCard
          label="Support signal"
          value={risk ? `${risk.score}` : 'Steady'}
          hint={risk ? `${risk.band} band · ${risk.status.toLowerCase()}` : 'No active risk flag'}
          icon={<ShieldAlert size={18} />}
          tone={risk?.band === 'HIGH' ? 'danger' : risk?.band === 'MEDIUM' ? 'warn' : 'good'}
        />
      </section>

      <div className="profile-grid">
        <div className="profile-main">
          <section className="card">
            <div className="card-head">
              <div>
                <h2>Attendance by subject</h2>
                <p className="muted small">Term-to-date · threshold {profile.attendanceThreshold}%</p>
              </div>
              <CalendarCheck2 size={18} />
            </div>
            {profile.attendanceBySubject.length === 0 ? (
              <p className="muted small">No attendance has been recorded yet.</p>
            ) : (
              <div className="subject-stat-list">
                {profile.attendanceBySubject.map((subject) => (
                  <div className="subject-stat-row" key={subject.subjectId}>
                    <span className="subject-code">{subject.code}</span>
                    <span className="subject-stat-copy">
                      <strong>{subject.name}</strong>
                      <small>{subject.present} present of {subject.held} classes</small>
                    </span>
                    <span className={`subject-percent ${subject.belowThreshold ? 'text-danger' : ''}`}>{percent(subject.percent)}</span>
                    <ProgressBar value={subject.percent ?? 0} />
                  </div>
                ))}
              </div>
            )}
            <div className="card-footline">
              <span><CalendarCheck2 size={14} /> Present count includes late arrivals.</span>
              <Link to="/attendance" className="text-link">Attendance desk <ArrowRight size={14} /></Link>
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <div>
                <h2>Internal marks</h2>
                <p className="muted small">Components are shown exactly as entered; moderation status is subject-level.</p>
              </div>
              <ClipboardList size={18} />
            </div>
            {profile.marksBySubject.length === 0 ? (
              <p className="muted small">No internal marks have been entered.</p>
            ) : (
              <div className="marks-summary-list">
                {profile.marksBySubject.map((subject) => (
                  <div className="marks-summary-row" key={subject.subjectId}>
                    <div className="marks-summary-head">
                      <span className="subject-code">{subject.code}</span>
                      <span className="subject-stat-copy">
                        <strong>{subject.name}</strong>
                        <small>{subject.moderationStatus}</small>
                      </span>
                      <span className="marks-total">
                        <strong>{subject.total === null ? '—' : subject.total.toFixed(1)}</strong>
                        <small>/{subject.maxTotal}</small>
                      </span>
                      <Link to={`/marks/${subject.subjectId}`} className="icon-link" aria-label={`Open marks sheet for ${subject.name}`}>
                        <ArrowRight size={15} />
                      </Link>
                    </div>
                    <div className="component-chips">
                      {subject.components.map((component) => (
                        <span className="component-chip" key={component.componentId}>
                          {component.name}
                          <strong>{component.score === null ? '—' : component.score}</strong>
                          <small>/{component.maxScore}</small>
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className={`card ${risk ? `risk-summary-card risk-${risk.band.toLowerCase()}` : ''}`}>
            <div className="card-head">
              <div>
                <h2>Support signal</h2>
                <p className="muted small">Rules-based and explainable. No action is taken without a mentor task.</p>
              </div>
              {risk ? <ScoreDial score={risk.score} band={risk.band} /> : <BadgeCheck size={20} />}
            </div>
            {!risk ? (
              <div className="quiet-block good">
                <BadgeCheck size={20} />
                <p>No active risk flag. Attendance and internal marks are within the watch lines.</p>
              </div>
            ) : (
              <>
                <div className="risk-metrics profile-risk-metrics">
                  <div><span>Attendance</span><strong>{percent(risk.attendancePercent)}</strong></div>
                  <div><span>Internal marks</span><strong>{percent(risk.marksPercent)}</strong></div>
                  <div><span>Recent trend</span><strong className={risk.trendDelta < 0 ? 'text-danger' : ''}>{risk.trendDelta > 0 ? '+' : ''}{risk.trendDelta.toFixed(0)} pts</strong></div>
                  <div><span>Band</span><strong>{risk.band}</strong></div>
                </div>
                <ul className="reason-list">
                  {risk.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                </ul>
                {risk.mentorTask ? (
                  <div className="mentor-task">
                    <span className="mentor-icon"><UserRoundCheck size={16} /></span>
                    <span>
                      <strong>{risk.mentorTask.assigneeName} · due {shortDate(risk.mentorTask.dueDate)}</strong>
                      <small>{risk.mentorTask.note}</small>
                    </span>
                    <Badge>{risk.mentorTask.status}</Badge>
                  </div>
                ) : (
                  <p className="muted small">No mentor task yet. A task is the only automated follow-up.</p>
                )}
              </>
            )}
          </section>

          {profile.fee && (
            <section className="card">
              <div className="card-head">
                <div>
                  <h2>Fee account</h2>
                  <p className="muted small">{profile.fee.account.status} · due {shortDate(profile.fee.account.dueDate)} · {profile.academicYear}</p>
                </div>
                <Wallet size={18} />
              </div>
              <div className="fee-summary-strip">
                <div><span>Billed</span><strong>{money(profile.fee.account.grossAmount - profile.fee.account.scholarshipAmount)}</strong></div>
                <div><span>Paid</span><strong>{money(profile.fee.account.paidAmount)}</strong></div>
                <div><span>Outstanding</span><strong className={profile.fee.account.dueAmount > 0 ? 'text-danger' : ''}>{money(profile.fee.account.dueAmount)}</strong></div>
              </div>
              {profile.fee.payments.length > 0 ? (
                <div className="payment-history">
                  {profile.fee.payments.map((payment) => (
                    <div className="payment-row" key={payment.id}>
                      <span className="receipt-icon"><Wallet size={14} /></span>
                      <span>
                        <strong>{payment.receiptNo}</strong>
                        <small>{payment.method} · {dateTime(payment.paidAt)} · {payment.recordedBy}</small>
                      </span>
                      <strong>{money(payment.amount)}</strong>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted small">No payments recorded yet.</p>
              )}
            </section>
          )}
        </div>

        <aside className="profile-aside">
          <section className="card">
            <div className="card-head compact">
              <h2>Student record</h2>
              <GraduationCap size={18} />
            </div>
            <div className="detail-list">
              <div><span>Programme</span><strong>{profile.programmeCode}</strong></div>
              <div><span>Department</span><strong>{profile.department}</strong></div>
              <div><span>Study year</span><strong>Year {profile.studyYear}, Section {profile.section}</strong></div>
              <div><span>Batch</span><strong>{profile.batch}</strong></div>
              <div><span>Category</span><strong>{profile.category}</strong></div>
              <div><span>Gender</span><strong>{profile.gender}</strong></div>
              <div><span>Admitted</span><strong>{longDate(profile.admittedOn)}</strong></div>
              <div><span>Academic year</span><strong>{profile.academicYear}</strong></div>
            </div>
          </section>

          <section className="card">
            <div className="card-head compact">
              <h2>Guardian & consent</h2>
              <Badge>{profile.consentStatus}</Badge>
            </div>
            <div className="detail-list">
              <div><span>Guardian</span><strong>{profile.guardianName}</strong></div>
              <div><span>Phone</span><strong>{profile.guardianPhone}</strong></div>
              <div><span>Email</span><strong className="break">{profile.email}</strong></div>
            </div>
            <p className="muted small consent-note">
              Consent controls whether automated fee reminders may be sent. Academic support records remain visible to authorised staff.
            </p>
          </section>

          <section className="card support-note-card">
            <div className="card-head compact">
              <h2>Office note</h2>
            </div>
            <p className="muted small">
              This 360 view is read-only. Use the relevant workspace to correct attendance, enter marks, record a fee payment or assign a mentor.
            </p>
            <Link to="/students" className="btn btn-secondary btn-block">
              <ArrowLeft size={15} /> Back to directory
            </Link>
          </section>
        </aside>
      </div>
    </div>
  );
}
