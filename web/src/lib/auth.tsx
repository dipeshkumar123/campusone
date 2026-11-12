import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken } from './api';
import type { User } from './types';

const USER_KEY = 'campusone.user';

interface AuthValue {
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<User>;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

function readUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getToken() ? readUser() : null));
  const [token, setAuthToken] = useState<string | null>(() => getToken());

  const logout = useCallback(() => {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
    setAuthToken(null);
    Object.keys(localStorage)
      .filter((key) => key.startsWith('campusone.roster.') || key.startsWith('campusone.attendance.outbox'))
      .forEach((key) => localStorage.removeItem(key));
  }, []);

  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener('campusone:unauthorized', onUnauthorized);
    return () => window.removeEventListener('campusone:unauthorized', onUnauthorized);
  }, [logout]);

  useEffect(() => {
    if (!getToken() || !navigator.onLine) return;
    api
      .me()
      .then((fresh) => {
        localStorage.setItem(USER_KEY, JSON.stringify(fresh));
        setUser(fresh);
      })
      .catch(() => undefined);
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api.login(email, password);
    setToken(response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    setUser(response.user);
    setAuthToken(response.token);
    return response.user;
  }, []);

  const value = useMemo(() => ({ user, token, login, logout }), [user, token, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
