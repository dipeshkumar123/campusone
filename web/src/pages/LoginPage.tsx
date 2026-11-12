import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  CalendarCheck2,
  Check,
  GraduationCap,
  Loader2,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  Wallet,
  WifiOff,
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { ApiError } from '../lib/api';

const demos = [
  {
    role: 'Office admin',
    email: 'admin@campusone.demo',
    password: 'Campus@2026',
    copy: 'Fees, moderation, at-risk students and the read-only assistant.',
    icon: ShieldCheck,
  },
  {
    role: 'Faculty',
    email: 'faculty@campusone.demo',
    password: 'Faculty@2026',
    copy: 'Offline attendance, internal marks and your teaching scope.',
    icon: CalendarCheck2,
  },
];

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (user) navigate('/', { replace: true });
  }, [user, navigate]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(email.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const chooseDemo = (demo: (typeof demos)[number]) => {
    setEmail(demo.email);
    setPassword(demo.password);
    setError('');
  };

  return (
    <div className="login-shell">
      <section className="login-story">
        <div className="login-story-top">
          <span className="brand-mark large">
            <GraduationCap size={28} />
          </span>
          <span className="login-wordmark">CampusOne</span>
          <span className="login-year">2026-27</span>
        </div>

        <div className="login-story-copy">
          <p className="eyebrow light">Bhubaneswar Campus</p>
          <h1>
            The quiet operating layer
            <br />
            for a busy campus.
          </h1>
          <p>
            One connected workspace for attendance, internal marks, fee collection and student support - designed to work
            when the network does not.
          </p>
          <ul className="login-features">
            <li>
              <span><WifiOff size={16} /></span>
              Mark attendance offline. It syncs once, safely.
            </li>
            <li>
              <span><LockKeyhole size={16} /></span>
              Internal marks stay draft until moderation locks them.
            </li>
            <li>
              <span><Wallet size={16} /></span>
              Fee follow-ups and receipts in the same place.
            </li>
            <li>
              <span><Sparkles size={16} /></span>
              Ask plain questions; get a read-only answer with its query plan.
            </li>
          </ul>
        </div>

        <div className="login-preview">
          <div className="preview-head">
            <span>Today · CSE301</span>
            <span className="preview-live">Ready to mark</span>
          </div>
          <div className="preview-row">
            <span className="preview-avatar mint">SM</span>
            <span>
              <strong>Subhashree Mohanty</strong>
              <small>ENG-24-CSE-0412</small>
            </span>
            <span className="preview-seg">
              <span className="on">P</span>
              <span>A</span>
              <span>L</span>
            </span>
          </div>
          <div className="preview-row">
            <span className="preview-avatar clay">SN</span>
            <span>
              <strong>Sneha Patra</strong>
              <small>ENG-24-CSE-0405</small>
            </span>
            <span className="preview-seg">
              <span>P</span>
              <span className="on absent">A</span>
              <span>L</span>
            </span>
          </div>
          <p className="preview-note">
            <Check size={14} /> 21 present · 2 absent · saved on this device
          </p>
        </div>

        <p className="login-story-foot">A demo workspace with synthetic students, marks and fees. No real student data.</p>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <p className="eyebrow">Welcome back</p>
          <h2>Sign in to CampusOne</h2>
          <p className="login-sub">Use a demo account below, or enter your campus credentials.</p>

          <form onSubmit={submit} className="login-form">
            <label className="field">
              <span className="field-label">Email</span>
              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="you@campusone.demo"
                required
              />
            </label>
            <label className="field">
              <span className="field-label">Password</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Your password"
                required
              />
            </label>

            {error && <div className="form-error">{error}</div>}

            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={busy}>
              {busy ? <Loader2 size={17} className="spin" /> : null}
              {busy ? 'Signing in' : 'Sign in'}
              {!busy && <ArrowRight size={17} />}
            </button>
          </form>

          <div className="demo-divider">
            <span>Demo accounts</span>
          </div>

          <div className="demo-list">
            {demos.map((demo) => (
              <button
                key={demo.email}
                type="button"
                className={`demo-card ${email === demo.email ? 'selected' : ''}`}
                onClick={() => chooseDemo(demo)}
              >
                <span className="demo-icon">
                  <demo.icon size={18} />
                </span>
                <span>
                  <strong>{demo.role}</strong>
                  <small>{demo.copy}</small>
                </span>
                <span className="demo-action">{email === demo.email ? 'Filled' : 'Use'}</span>
              </button>
            ))}
          </div>

          <p className="login-legal">
            Demo passwords: <code>Campus@2026</code> for the office, <code>Faculty@2026</code> for teaching staff.
          </p>
        </div>
      </section>
    </div>
  );
}
