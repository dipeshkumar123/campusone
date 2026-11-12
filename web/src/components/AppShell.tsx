import { useEffect, useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  CalendarCheck2,
  ClipboardList,
  CloudUpload,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldAlert,
  Sparkles,
  Users,
  Wallet,
  Wifi,
  WifiOff,
  X,
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { pendingCount } from '../lib/outbox';
import { Avatar } from './ui';

interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  primary?: boolean;
}

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [online, setOnline] = useState(navigator.onLine);
  const [queued, setQueued] = useState(() => pendingCount());

  const navItems: NavItem[] = useMemo(() => {
    if (!user) return [];
    if (user.role === 'ADMIN') {
      return [
        { to: '/', label: 'Overview', icon: LayoutDashboard, primary: true },
        { to: '/students', label: 'Students', icon: Users, primary: true },
        { to: '/marks', label: 'Marks', icon: ClipboardList, primary: true },
        { to: '/fees', label: 'Fees', icon: Wallet, primary: true },
        { to: '/risk', label: 'At-risk', icon: ShieldAlert },
        { to: '/assistant', label: 'Ask CampusOne', icon: Sparkles },
      ];
    }
    return [
      { to: '/', label: 'Overview', icon: LayoutDashboard, primary: true },
      { to: '/attendance', label: 'Attendance', icon: CalendarCheck2, primary: true },
      { to: '/marks', label: 'Marks', icon: ClipboardList, primary: true },
      { to: '/risk', label: 'At-risk', icon: ShieldAlert, primary: true },
      { to: '/students', label: 'Students', icon: Users },
    ];
  }, [user]);

  useEffect(() => {
    const updateOnline = () => setOnline(navigator.onLine);
    const updateQueue = () => setQueued(pendingCount());
    window.addEventListener('online', updateOnline);
    window.addEventListener('offline', updateOnline);
    window.addEventListener('campusone:outbox', updateQueue);
    return () => {
      window.removeEventListener('online', updateOnline);
      window.removeEventListener('offline', updateOnline);
      window.removeEventListener('campusone:outbox', updateQueue);
    };
  }, []);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  const current = navItems.find((item) => (item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)));

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  if (!user) return null;

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">
            <GraduationCap size={22} />
          </span>
          <span>
            <strong>CampusOne</strong>
            <small>College operations</small>
          </span>
        </div>

        <nav className="sidebar-nav" aria-label="Primary navigation">
          <p className="nav-label">Workspace</p>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <item.icon size={18} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className={`connection-chip ${online ? 'is-online' : 'is-offline'}`}>
            {online ? <Wifi size={14} /> : <WifiOff size={14} />}
            {online ? 'Online' : 'Offline mode'}
          </div>
          {queued > 0 && (
            <div className="queue-chip">
              <CloudUpload size={14} />
              {queued} attendance {queued === 1 ? 'class' : 'classes'} queued
            </div>
          )}
          <div className="user-card">
            <Avatar name={user.name} size="sm" />
            <span className="user-copy">
              <strong>{user.name}</strong>
              <small>{user.title}</small>
            </span>
            <button type="button" className="icon-btn" onClick={handleLogout} aria-label="Sign out" title="Sign out">
              <LogOut size={16} />
            </button>
          </div>
          <p className="sidebar-note">{user.campusName} · Demo workspace</p>
        </div>
      </aside>

      <div className="main-column">
        <header className="mobile-bar">
          <button type="button" className="icon-btn" onClick={() => setDrawerOpen(true)} aria-label="Open navigation">
            <Menu size={20} />
          </button>
          <span className="mobile-brand">
            <span className="brand-mark small">
              <GraduationCap size={18} />
            </span>
            CampusOne
          </span>
          <button type="button" className="icon-btn" onClick={handleLogout} aria-label="Sign out">
            <LogOut size={18} />
          </button>
        </header>

        <div className="desktop-topline">
          <span>
            {user.campusName} <span className="dot-sep">·</span> {current?.label ?? 'CampusOne'}
          </span>
          <span className="topline-right">
            <span className={`online-dot ${online ? 'on' : 'off'}`} />
            {online ? 'Connected' : 'Working offline'}
          </span>
        </div>

        <main className="page-wrap">
          <Outlet />
        </main>

        <nav className="bottom-nav" aria-label="Quick navigation">
          {navItems
            .filter((item) => item.primary)
            .slice(0, 4)
            .map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) => `bottom-link ${isActive ? 'active' : ''}`}
              >
                <item.icon size={20} />
                <span>{item.label}</span>
              </NavLink>
            ))}
          <button type="button" className="bottom-link" onClick={() => setDrawerOpen(true)}>
            <Menu size={20} />
            <span>More</span>
          </button>
        </nav>
      </div>

      {drawerOpen && (
        <div className="drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setDrawerOpen(false)}>
          <aside className="drawer" aria-label="Navigation drawer">
            <div className="drawer-head">
              <div className="brand">
                <span className="brand-mark">
                  <GraduationCap size={22} />
                </span>
                <span>
                  <strong>CampusOne</strong>
                  <small>{user.campusName}</small>
                </span>
              </div>
              <button type="button" className="icon-btn" onClick={() => setDrawerOpen(false)} aria-label="Close navigation">
                <X size={18} />
              </button>
            </div>
            <nav className="sidebar-nav">
              {navItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  <item.icon size={18} />
                  <span>{item.label}</span>
                </NavLink>
              ))}
            </nav>
            <div className="sidebar-foot">
              <div className="user-card">
                <Avatar name={user.name} size="sm" />
                <span className="user-copy">
                  <strong>{user.name}</strong>
                  <small>{user.roleLabel}</small>
                </span>
              </div>
              <button type="button" className="btn btn-secondary btn-block" onClick={handleLogout}>
                <LogOut size={16} /> Sign out
              </button>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
