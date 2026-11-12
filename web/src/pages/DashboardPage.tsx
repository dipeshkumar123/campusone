import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  BookOpenCheck,
  CalendarCheck2,
  CheckCircle2,
  CircleDollarSign,
  ClipboardCheck,
  Clock3,
  ShieldAlert,
  Sparkles,
  TrendingDown,
  Users,
  Wallet,
} from 'lucide-react';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { compactMoney, money, percent, periodLabel, relativeTime, shortDate } from '../lib/format';
import { Avatar, Badge, ErrorState, ProgressBar, Sparkline, Spinner, StatCard } from '../components/ui';

export function DashboardPage() {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard });
  const isAdmin = user?.role === 'ADMIN';

  if (query.isLoading) return <Spinner label="Preparing your campus snapshot" />;
  if (query.isError) {
    return <ErrorState message={(query.error as Error).message} onRetry={() => query.refetch()} />;
  }

  const data = query.data!;
  const metrics = data.metrics;
  const greeting = greetingForNow();
  const firstName = user?.name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.|Mrs\.)\s+/i, '').split(' ')[0] ?? 'there';
  const submitted = data.classes.filter((item) => item.submitted).length;
  const attendancePoints = data.attendanceTrend.map((point) => ({ label: point.label, value: point.attendancePercent }));

  return (
    <div className="dashboard">
      <section className="welcome">
        <div>
          <p className="eyebrow">{data.dayLabel} · {data.academicYear}</p>
          <h1>{greeting}, {firstName}.</h1>
          <p className="welcome-sub">{data.scopeNote}</p>
        </div>
        <div className="welcome-actions">
          {isAdmin ? (
            <>
              <Link to="/risk" className="btn btn-secondary">
                <ShieldAlert size={16} /> Review at-risk
              </Link>
              <Link to="/fees" className="btn btn-primary">
                <Wallet size={16} /> Fee desk
              </Link>
            </>
          ) : (
            <>
              <Link to="/marks" className="btn btn-secondary">
                <ClipboardCheck size={16} /> Enter marks
              </Link>
              {data.classes[0] && (
                <Link
                  to={`/attendance/mark?subjectId=${data.classes[0].subjectId}&date=${data.today}&period=${data.classes[0].period}`}
                  className="btn btn-primary"
                >
                  <CalendarCheck2 size={16} /> Mark attendance
                </Link>
              )}
            </>
          )}
        </div>
      </section>

      <section className="stat-grid">
        {isAdmin ? (
          <>
            <StatCard
              label="Active students"
              value={metrics.activeStudents}
              hint={`${data.classes.length} classes scheduled today`}
              icon={<Users size={18} />}
              tone="brand"
            />
            <StatCard
              label="Fee outstanding"
              value={compactMoney(metrics.feeOutstanding)}
              hint={`${metrics.defaulters} students with dues`}
              icon={<CircleDollarSign size={18} />}
              tone={metrics.defaulters > 0 ? 'warn' : 'good'}
            />
            <StatCard
              label="Open risk flags"
              value={metrics.openRiskFlags}
              hint={`${metrics.mentorTasksOpen} mentor tasks open`}
              icon={<ShieldAlert size={18} />}
              tone={metrics.openRiskFlags > 0 ? 'danger' : 'good'}
            />
            <StatCard
              label="Pending moderation"
              value={metrics.pendingModeration}
              hint={`${metrics.classesSubmittedToday}/${metrics.classesScheduledToday} classes marked today`}
              icon={<BookOpenCheck size={18} />}
              tone={metrics.pendingModeration > 0 ? 'warn' : 'good'}
            />
          </>
        ) : (
          <>
            <StatCard
              label="Today's classes"
              value={data.classes.length}
              hint={`${submitted} of ${data.classes.length} submitted`}
              icon={<CalendarCheck2 size={18} />}
              tone="brand"
            />
            <StatCard
              label="Attendance this week"
              value={percent(metrics.attendanceThisWeek)}
              hint={`${metrics.belowThreshold} students under ${data.classes.length ? 'the 75% line' : 'threshold'}`}
              icon={<TrendingDown size={18} />}
              tone={(metrics.attendanceThisWeek ?? 0) >= 85 ? 'good' : 'warn'}
            />
            <StatCard
              label="Students in scope"
              value={metrics.activeStudents}
              hint={`${metrics.pendingModeration} subjects awaiting moderation`}
              icon={<Users size={18} />}
            />
            <StatCard
              label="Open risk flags"
              value={metrics.openRiskFlags}
              hint={`${metrics.mentorTasksOpen} mentor tasks with faculty`}
              icon={<ShieldAlert size={18} />}
              tone={metrics.openRiskFlags > 0 ? 'danger' : 'good'}
            />
          </>
        )}
      </section>

      <div className="dashboard-grid">
        <div className="dashboard-main">
          <section className="card">
            <div className="card-head">
              <div>
                <h2>Today’s classes</h2>
                <p className="muted small">{data.dayLabel} · {data.classes.length ? `${submitted} submitted` : 'No scheduled classes'}</p>
              </div>
              {!isAdmin && (
                <Link to="/attendance" className="text-link">
                  Attendance desk <ArrowRight size={14} />
                </Link>
              )}
            </div>
            {data.classes.length === 0 ? (
              <div className="quiet-block">
                <Clock3 size={20} />
                <p>No classes are scheduled for today. A good day to catch up on moderation.</p>
              </div>
            ) : (
              <div className="class-list">
                {data.classes.map((item) => (
                  <div className="class-row" key={`${item.subjectId}-${item.period}`}>
                    <span className="period-chip">P{item.period}</span>
                    <span className="class-copy">
                      <strong>{item.subjectName}</strong>
                      <small>
                        {item.subjectCode} · Year {item.studyYear} · {item.room}
                        {isAdmin ? ` · ${item.facultyName}` : ''}
                      </small>
                    </span>
                    <span className="class-time">{periodLabel(item.period).split('·')[1]?.trim()}</span>
                    {item.submitted ? (
                      <span className="class-status good">
                        <CheckCircle2 size={15} /> {item.presentCount}/{item.studentCount} present
                      </span>
                    ) : (
                      <span className="class-status waiting">
                        Not marked
                        {!isAdmin && (
                          <Link
                            to={`/attendance/mark?subjectId=${item.subjectId}&date=${data.today}&period=${item.period}`}
                            className="inline-action"
                          >
                            Start
                          </Link>
                        )}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="card">
            <div className="card-head">
              <div>
                <h2>Attendance rhythm</h2>
                <p className="muted small">Last {data.attendanceTrend.length} teaching weeks · all classes in scope</p>
              </div>
              <span className={`trend-pill ${(metrics.attendanceThisWeek ?? 0) >= 85 ? 'good' : 'warn'}`}>
                {percent(metrics.attendanceThisWeek)} this week
              </span>
            </div>
            <Sparkline points={attendancePoints} threshold={75} />
          </section>

          <section className="card">
            <div className="card-head">
              <div>
                <h2>Students who may need a check-in</h2>
                <p className="muted small">Rules-based watchlist · scores are explainable, never acted on automatically</p>
              </div>
              <Link to="/risk" className="text-link">
                All flags <ArrowRight size={14} />
              </Link>
            </div>
            {data.risks.length === 0 ? (
              <div className="quiet-block">
                <CheckCircle2 size={20} />
                <p>No students are on the watchlist right now.</p>
              </div>
            ) : (
              <div className="risk-preview-list">
                {data.risks.slice(0, 4).map((risk) => (
                  <Link to={`/students/${risk.studentId}`} className="risk-preview-row" key={risk.flagId}>
                    <Avatar name={risk.studentName} size="sm" />
                    <span className="class-copy">
                      <strong>{risk.studentName}</strong>
                      <small>{risk.rollNo} · {risk.programmeCode}</small>
                    </span>
                    <span className={`mini-meter ${risk.band.toLowerCase()}`}>
                      <span style={{ width: `${risk.score}%` }} />
                    </span>
                    <span className="risk-score">{risk.score}</span>
                    <Badge>{risk.band}</Badge>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="dashboard-aside">
          <section className="card automation-card">
            <div className="card-head compact">
              <h2>Automation desk</h2>
              <span className="live-chip"><span /> live</span>
            </div>
            <div className="automation-row">
              <span className="automation-icon warn"><Clock3 size={17} /></span>
              <div>
                <strong>{data.automation.reminderCandidates} fee reminders ready</strong>
                <small>Last run {relativeTime(data.automation.lastReminderAt).toLowerCase()}</small>
              </div>
            </div>
            <div className="automation-row">
              <span className="automation-icon brand"><ShieldAlert size={17} /></span>
              <div>
                <strong>{data.automation.atRiskAlerts} at-risk alerts</strong>
                <small>Refreshed {relativeTime(data.automation.alertsRefreshedAt).toLowerCase()}</small>
              </div>
            </div>
            <div className="automation-actions">
              {isAdmin && (
                <Link to="/fees" className="btn btn-secondary btn-sm">
                  Send reminders
                </Link>
              )}
              <Link to="/risk" className="btn btn-secondary btn-sm">
                Mentor queue
              </Link>
            </div>
          </section>

          {isAdmin ? (
            <>
              <section className="card">
                <div className="card-head compact">
                  <h2>Fee follow-up</h2>
                  <Link to="/fees" className="icon-link" aria-label="Open fee desk">
                    <ArrowRight size={16} />
                  </Link>
                </div>
                <div className="fee-focus-value">
                  <strong>{compactMoney(metrics.feeOutstanding)}</strong>
                  <span>outstanding across {data.dues.length} shown dues</span>
                </div>
                <ProgressBar value={100 - (metrics.defaulters ? (metrics.defaulters / Math.max(1, metrics.activeStudents)) * 100 : 100)} />
                <div className="mini-rows">
                  {data.dues.slice(0, 3).map((due) => (
                    <Link to={`/students/${due.studentId}`} key={due.studentId} className="mini-row">
                      <span>
                        <strong>{due.studentName}</strong>
                        <small>{due.rollNo}</small>
                      </span>
                      <span className="amount-due">{money(due.dueAmount)}</span>
                    </Link>
                  ))}
                </div>
              </section>

              <section className="card">
                <div className="card-head compact">
                  <h2>Moderation queue</h2>
                  <Link to="/marks" className="icon-link" aria-label="Open marks">
                    <ArrowRight size={16} />
                  </Link>
                </div>
                {data.pendingModeration.length === 0 ? (
                  <p className="muted small">Everything in scope is moderated.</p>
                ) : (
                  <div className="moderation-list">
                    {data.pendingModeration.map((item) => (
                      <Link to={`/marks/${item.subjectId}`} className="moderation-row" key={item.subjectId}>
                        <span>
                          <strong>{item.code}</strong>
                          <small>{item.name}</small>
                        </span>
                        <span className="moderation-progress">
                          <span>{item.enteredScores}/{item.expectedScores}</span>
                          <ProgressBar value={(item.enteredScores / Math.max(1, item.expectedScores)) * 100} />
                        </span>
                      </Link>
                    ))}
                  </div>
                )}
              </section>
            </>
          ) : (
            <section className="card support-card">
              <div className="card-head compact">
                <h2>Your teaching scope</h2>
              </div>
              <p className="muted small">You can take attendance and enter marks only for the subjects assigned to you.</p>
              <div className="scope-stat">
                <span>Internal marks completion</span>
                <strong>{data.pendingModeration.length === 0 ? 'All clear' : `${data.pendingModeration.length} subjects pending`}</strong>
              </div>
              <Link to="/students" className="btn btn-secondary btn-block">
                <Users size={16} /> View my students
              </Link>
            </section>
          )}

          {isAdmin && (
            <Link to="/assistant" className="assistant-card">
              <span className="assistant-mark"><Sparkles size={20} /></span>
              <span>
                <strong>Ask CampusOne</strong>
                <small>Plain-language questions, read-only answers, visible query plan.</small>
              </span>
              <ArrowRight size={16} />
            </Link>
          )}

          <section className="card compact-card">
            <h2>Quick read</h2>
            <div className="quick-read-row">
              <span>Classes marked today</span>
              <strong>{metrics.classesSubmittedToday}/{metrics.classesScheduledToday}</strong>
            </div>
            <div className="quick-read-row">
              <span>Students below threshold</span>
              <strong>{metrics.belowThreshold}</strong>
            </div>
            <div className="quick-read-row">
              <span>Last assessment refresh</span>
              <strong>{shortDate(data.automation.alertsRefreshedAt)}</strong>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}

function greetingForNow(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}
