import { useEffect, type ReactNode } from 'react';
import { AlertTriangle, Inbox, Loader2, RefreshCw, X } from 'lucide-react';

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="loading-state" role="status">
      <Loader2 className="spin" size={22} />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-card error-state">
      <span className="state-icon">
        <AlertTriangle size={20} />
      </span>
      <div>
        <strong>That did not load</strong>
        <p>{message}</p>
      </div>
      {onRetry && (
        <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>
          <RefreshCw size={14} /> Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="state-card empty-state">
      <span className="state-icon">
        <Inbox size={20} />
      </span>
      <div>
        <strong>{title}</strong>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function StatCard({
  label,
  value,
  hint,
  icon,
  tone = 'default',
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  tone?: 'default' | 'good' | 'warn' | 'danger' | 'brand';
}) {
  return (
    <div className={`stat-card stat-${tone}`}>
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        {icon && <span className="stat-icon">{icon}</span>}
      </div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}

const badgeTones: Record<string, string> = {
  HIGH: 'danger',
  MEDIUM: 'warn',
  LOW: 'good',
  OPEN: 'warn',
  ACKNOWLEDGED: 'info',
  RESOLVED: 'good',
  LOCKED: 'good',
  DRAFT: 'neutral',
  SUCCESS: 'good',
  PAID: 'good',
  PARTIAL: 'warn',
  DUE: 'danger',
  OVERDUE: 'danger',
  PRESENT: 'good',
  ABSENT: 'danger',
  LATE: 'warn',
  UNMARKED: 'neutral',
  ACTIVE: 'good',
  CONSENTED: 'good',
  PENDING: 'warn',
  DONE: 'good',
  ADMIN: 'brand',
  FACULTY: 'info',
};

export function Badge({ children, tone }: { children: ReactNode; tone?: string }) {
  const key = typeof children === 'string' ? children.toUpperCase().replace(/\s+/g, '_') : '';
  const resolved = tone ?? badgeTones[key] ?? 'neutral';
  return <span className={`badge badge-${resolved}`}>{children}</span>;
}

export function Avatar({ name, size = 'md', tone }: { name: string; size?: 'sm' | 'md' | 'lg'; tone?: string }) {
  const letters = name
    .replace(/^(Dr\.|Prof\.|Mr\.|Ms\.|Mrs\.)\s+/i, '')
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
  const palette = ['mint', 'clay', 'sky', 'sand', 'plum'];
  const index = name.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % palette.length;
  return <span className={`avatar avatar-${size} ${tone ?? `avatar-${palette[index]}`}`}>{letters}</span>;
}

export function ProgressBar({ value, tone }: { value: number; tone?: 'good' | 'warn' | 'danger' }) {
  const clamped = Math.max(0, Math.min(100, value));
  const resolved = tone ?? (clamped >= 85 ? 'good' : clamped >= 75 ? 'warn' : 'danger');
  return (
    <div className="progress-track" aria-hidden="true">
      <div className={`progress-fill progress-${resolved}`} style={{ width: `${clamped}%` }} />
    </div>
  );
}

export function ScoreDial({ score, band, size = 54 }: { score: number; band: string; size?: number }) {
  const radius = (size - 6) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - Math.min(100, Math.max(0, score)) / 100);
  const color = band === 'HIGH' ? '#B4442E' : band === 'MEDIUM' ? '#A66A1F' : '#2C7A5B';
  return (
    <span className="score-dial" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`Risk score ${score}`}>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--line)" strokeWidth="4" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth="4"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <strong>{score}</strong>
    </span>
  );
}

export function Sparkline({
  points,
  width = 380,
  height = 110,
  threshold,
}: {
  points: Array<{ label: string; value: number | null }>;
  width?: number;
  height?: number;
  threshold?: number;
}) {
  const valid = points.map((point, index) => ({ ...point, index })).filter((point) => point.value !== null);
  if (valid.length === 0) return <p className="muted small">No attendance observations yet.</p>;
  const padX = 10;
  const padY = 14;
  const min = 50;
  const max = 100;
  const xFor = (index: number) => padX + (index / Math.max(1, points.length - 1)) * (width - padX * 2);
  const yFor = (value: number) => padY + ((max - value) / (max - min)) * (height - padY * 2);
  const line = valid.map((point) => `${xFor(point.index)},${yFor(point.value ?? 0)}`).join(' ');
  const area = `${padX},${height - padY} ${line} ${xFor(points.length - 1)},${height - padY}`;
  const thresholdY = threshold !== undefined ? yFor(threshold) : null;
  const last = valid[valid.length - 1];

  return (
    <div className="sparkline">
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Weekly attendance trend">
        <defs>
          <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#2C7A5B" stopOpacity="0.22" />
            <stop offset="100%" stopColor="#2C7A5B" stopOpacity="0" />
          </linearGradient>
        </defs>
        {thresholdY !== null && (
          <line x1={padX} x2={width - padX} y1={thresholdY} y2={thresholdY} stroke="#C9A45C" strokeDasharray="4 5" strokeWidth="1" />
        )}
        <polygon points={area} fill="url(#sparkFill)" />
        <polyline points={line} fill="none" stroke="#1E5C4B" strokeWidth="2.4" strokeLinejoin="round" strokeLinecap="round" />
        <circle cx={xFor(last.index)} cy={yFor(last.value ?? 0)} r="4" fill="#1E5C4B" stroke="#F5F4EF" strokeWidth="2" />
      </svg>
      <div className="spark-axis">
        {points.map((point) => (
          <span key={point.label}>{point.label}</span>
        ))}
      </div>
    </div>
  );
}

export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  wide,
}: {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div className="modal-head">
          <div>
            <h2 id="modal-title">{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close dialog">
            <X size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}
