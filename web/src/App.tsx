import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { useAuth } from './lib/auth';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { AttendancePage } from './pages/AttendancePage';
import { AttendanceMarkPage } from './pages/AttendanceMarkPage';
import { MarksPage } from './pages/MarksPage';
import { MarksSheetPage } from './pages/MarksSheetPage';
import { FeesPage } from './pages/FeesPage';
import { RiskPage } from './pages/RiskPage';
import { AssistantPage } from './pages/AssistantPage';
import { StudentsPage } from './pages/StudentsPage';
import { StudentProfilePage } from './pages/StudentProfilePage';
import { NotFoundPage } from './pages/NotFoundPage';
import type { Role } from './lib/types';

function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

function RoleGate({ role, children }: { role: Role; children: React.ReactNode }) {
  const { user } = useAuth();
  if (user?.role !== role) return <NotFoundPage forbidden />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route
            path="attendance"
            element={
              <RoleGate role="FACULTY">
                <AttendancePage />
              </RoleGate>
            }
          />
          <Route
            path="attendance/mark"
            element={
              <RoleGate role="FACULTY">
                <AttendanceMarkPage />
              </RoleGate>
            }
          />
          <Route path="marks" element={<MarksPage />} />
          <Route path="marks/:subjectId" element={<MarksSheetPage />} />
          <Route
            path="fees"
            element={
              <RoleGate role="ADMIN">
                <FeesPage />
              </RoleGate>
            }
          />
          <Route path="risk" element={<RiskPage />} />
          <Route
            path="assistant"
            element={
              <RoleGate role="ADMIN">
                <AssistantPage />
              </RoleGate>
            }
          />
          <Route path="students" element={<StudentsPage />} />
          <Route path="students/:studentId" element={<StudentProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
