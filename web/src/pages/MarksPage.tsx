import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, ClipboardList, LockKeyhole, Users } from 'lucide-react';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { Badge, ErrorState, PageHeader, ProgressBar, Spinner, StatCard } from '../components/ui';

export function MarksPage() {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['marks-subjects'], queryFn: api.marks.subjects });
  const isAdmin = user?.role === 'ADMIN';

  if (query.isLoading) return <Spinner label="Gathering subject sheets" />;
  if (query.isError) return <ErrorState message={(query.error as Error).message} onRetry={() => query.refetch()} />;

  const subjects = query.data ?? [];
  const locked = subjects.filter((subject) => subject.moderationStatus === 'LOCKED').length;
  const pending = subjects.length - locked;
  const enrolled = subjects.reduce((sum, subject) => sum + subject.enrolled, 0);
  const atRisk = subjects.reduce((sum, subject) => sum + subject.belowThresholdStudents, 0);

  return (
    <div>
      <PageHeader
        eyebrow="Academic records"
        title="Internal marks"
        description={
          isAdmin
            ? 'Review entry progress, lock a subject after moderation, and keep an auditable trail of every change.'
            : 'Enter internal assessment marks for your subjects. Drafts save as you go; moderation locks the sheet.'
        }
      />

      <section className="stat-grid four">
        <StatCard label="Subjects in scope" value={subjects.length} hint={isAdmin ? 'All programmes' : 'Your teaching load'} icon={<ClipboardList size={18} />} tone="brand" />
        <StatCard label="Awaiting moderation" value={pending} hint={`${locked} locked`} icon={<LockKeyhole size={18} />} tone={pending > 0 ? 'warn' : 'good'} />
        <StatCard label="Students assessed" value={enrolled} hint="Across visible subjects" icon={<Users size={18} />} />
        <StatCard label="Below attendance line" value={atRisk} hint="In these subjects" icon={<AlertTriangle size={18} />} tone={atRisk > 0 ? 'danger' : 'good'} />
      </section>

      <div className="subject-card-grid">
        {subjects.map((subject) => {
          const completion = (subject.enteredScores / Math.max(1, subject.expectedScores)) * 100;
          return (
            <Link to={`/marks/${subject.id}`} className="subject-card" key={subject.id}>
              <div className="subject-card-top">
                <span className="subject-code">{subject.code}</span>
                <Badge>{subject.moderationStatus}</Badge>
              </div>
              <h3>{subject.name}</h3>
              <p className="muted small">{subject.programmeName} · Year {subject.studyYear}</p>
              <div className="subject-progress">
                <div className="subject-progress-label">
                  <span>{subject.enteredScores}/{subject.expectedScores} scores</span>
                  <strong>{Math.round(completion)}%</strong>
                </div>
                <ProgressBar value={completion} />
              </div>
              <div className="subject-card-foot">
                <span><strong>{subject.enrolled}</strong> students</span>
                {subject.belowThresholdStudents > 0 && (
                  <span className="text-warn"><AlertTriangle size={13} /> {subject.belowThresholdStudents} below threshold</span>
                )}
                <span className="open-link">Open sheet <ArrowRight size={14} /></span>
              </div>
            </Link>
          );
        })}
      </div>

      {subjects.length === 0 && (
        <div className="quiet-block">
          <ClipboardList size={20} />
          <p>No subjects are assigned to your scope yet.</p>
        </div>
      )}
    </div>
  );
}
