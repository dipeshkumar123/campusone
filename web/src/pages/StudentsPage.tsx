import { useDeferredValue, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowRight, Search, Users } from 'lucide-react';
import { api } from '../lib/api';
import { money, percent } from '../lib/format';
import { Avatar, Badge, ErrorState, PageHeader, ProgressBar, Spinner } from '../components/ui';

export function StudentsPage() {
  const [search, setSearch] = useState('');
  const [programmeId, setProgrammeId] = useState<number | ''>('');
  const [year, setYear] = useState<number | ''>('');
  const deferredSearch = useDeferredValue(search);

  const metaQuery = useQuery({ queryKey: ['meta'], queryFn: api.meta });
  const query = useQuery({
    queryKey: ['students', deferredSearch, programmeId, year],
    queryFn: () => api.students({ query: deferredSearch, programmeId: programmeId || undefined, year: year || undefined }),
  });

  if (query.isLoading) return <Spinner label="Loading the student directory" />;
  if (query.isError || !query.data) {
    return <ErrorState message={(query.error as Error)?.message ?? 'Students could not be loaded.'} onRetry={() => query.refetch()} />;
  }

  const meta = metaQuery.data;
  const students = query.data.rows;
  const belowThreshold = students.filter((student) => (student.attendancePercent ?? 100) < (meta?.attendanceThreshold ?? 75)).length;

  return (
    <div>
      <PageHeader
        eyebrow="Student directory"
        title="Students"
        description="A single view of attendance, internal marks, fee position and support status. Scope follows your role."
      />

      <div className="directory-summary">
        <span><Users size={16} /><strong>{query.data.total}</strong> students in view</span>
        <span className="muted small">{belowThreshold} below the attendance threshold</span>
        <span className="muted small">{students.filter((student) => student.riskBand === 'HIGH').length} high-risk flags</span>
      </div>

      <section className="card">
        <div className="fee-toolbar">
          <div className="search-field">
            <Search size={16} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name or roll number"
              aria-label="Search students"
            />
          </div>
          <div className="filter-row">
            <select
              value={programmeId}
              onChange={(event) => setProgrammeId(event.target.value ? Number(event.target.value) : '')}
              aria-label="Filter by programme"
            >
              <option value="">All programmes</option>
              {(meta?.programmes ?? []).map((programme) => (
                <option value={programme.id} key={programme.id}>{programme.name}</option>
              ))}
            </select>
            <select
              value={year}
              onChange={(event) => setYear(event.target.value ? Number(event.target.value) : '')}
              aria-label="Filter by study year"
            >
              <option value="">All years</option>
              {(meta?.years ?? [1, 2, 3]).map((value) => (
                <option value={value} key={value}>Year {value}</option>
              ))}
            </select>
          </div>
        </div>

        {students.length === 0 ? (
          <div className="quiet-block">
            <Search size={20} />
            <p>No students match these filters.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data-table student-table">
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Programme</th>
                  <th>Attendance</th>
                  <th className="number-col">Internal /40</th>
                  <th className="number-col">Fee due</th>
                  <th>Support</th>
                  <th>Consent</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {students.map((student) => (
                  <tr key={student.id}>
                    <td>
                      <div className="table-student">
                        <Avatar name={student.name} size="sm" />
                        <span>
                          <Link to={`/students/${student.id}`}>{student.name}</Link>
                          <small>{student.rollNo} · {student.category}</small>
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="muted">{student.programmeCode}</span>
                      <br />
                      <small className="muted">Year {student.studyYear} · Sec {student.section}</small>
                    </td>
                    <td>
                      <div className="table-meter">
                        <span className={(student.attendancePercent ?? 100) < (meta?.attendanceThreshold ?? 75) ? 'text-danger' : ''}>
                          {percent(student.attendancePercent)}
                        </span>
                        <ProgressBar value={student.attendancePercent ?? 0} />
                      </div>
                    </td>
                    <td className="number-col">{student.internalMarks40 === null ? '—' : student.internalMarks40.toFixed(1)}</td>
                    <td className="number-col">
                      {student.feeDue > 0 ? <span className="text-danger">{money(student.feeDue)}</span> : <span className="muted">Clear</span>}
                    </td>
                    <td>
                      {student.riskBand ? <Badge>{student.riskBand}</Badge> : <span className="muted small">Steady</span>}
                    </td>
                    <td><Badge>{student.consentStatus}</Badge></td>
                    <td className="action-col">
                      <Link to={`/students/${student.id}`} className="icon-link" aria-label={`Open ${student.name}`}>
                        <ArrowRight size={16} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="table-foot">
          <span className="muted small">{students.length} records shown. Fee amounts are only visible to the office.</span>
        </div>
      </section>
    </div>
  );
}
