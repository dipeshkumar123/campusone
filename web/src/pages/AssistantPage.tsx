import { useRef, useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  ArrowUp,
  BookOpenCheck,
  Database,
  Lightbulb,
  Loader2,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  Table2,
  TriangleAlert,
} from 'lucide-react';
import { api, ApiError } from '../lib/api';
import { money, number as formatNumber, percent } from '../lib/format';
import type { AssistantAnswer } from '../lib/types';
import { Badge, PageHeader } from '../components/ui';

const starterQuestions = [
  'Show at-risk students in CSE',
  'Which students have fee dues above ₹40,000?',
  'How many second-year students are below 75% attendance this month?',
  'Average internal marks for second-year CSE',
  'Which students have guardian consent pending?',
  'Show students in Mechanical with attendance below 80%',
];

interface Turn {
  question: string;
  answer: AssistantAnswer;
}

export function AssistantPage() {
  const [question, setQuestion] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [error, setError] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  const mutation = useMutation({
    mutationFn: (value: string) => api.assistant.ask(value),
    onSuccess: (answer, value) => {
      setTurns((current) => [...current, { question: value, answer }]);
      setQuestion('');
      setError('');
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'The question could not be answered.'),
    onSettled: () => inputRef.current?.focus(),
  });

  const ask = (value: string) => {
    const trimmed = value.trim();
    if (trimmed.length < 4) {
      setError('Ask a slightly longer question.');
      return;
    }
    mutation.mutate(trimmed);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    ask(question);
  };

  const latest = turns[turns.length - 1];
  const examples = latest?.answer.examples?.length ? latest.answer.examples : starterQuestions;

  return (
    <div>
      <PageHeader
        eyebrow="Office tools · ADMIN only"
        title="Ask CampusOne"
        description="Ask a plain-language question about students, attendance, marks, fees or risk. Answers are read-only and always show the query behind them."
        actions={
          <span className="readonly-chip"><LockKeyhole size={14} /> Read-only</span>
        }
      />

      <div className="assistant-layout">
        <div className="assistant-main">
          <form className="ask-card" onSubmit={submit}>
            <div className="ask-head">
              <span className="assistant-mark large"><Sparkles size={22} /></span>
              <div>
                <h2>What would you like to know?</h2>
                <p className="muted small">This demo uses a deterministic question parser - no student records are sent to an external AI model.</p>
              </div>
            </div>
            <div className="ask-input">
              <input
                ref={inputRef}
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                maxLength={300}
                placeholder="e.g. Which CSE students are below 75% attendance this month?"
                aria-label="Ask a question"
              />
              <button type="submit" className="btn btn-primary" disabled={mutation.isPending || question.trim().length < 4}>
                {mutation.isPending ? <Loader2 size={16} className="spin" /> : <ArrowUp size={16} />}
                Ask
              </button>
            </div>
            {error && <div className="form-error">{error}</div>}
            <div className="ask-hints">
              <span><ShieldCheck size={13} /> Read-only SQL-style plan</span>
              <span><Database size={13} /> 23 synthetic students</span>
              <span><BookOpenCheck size={13} /> Scope respects the office</span>
            </div>
          </form>

          {!latest && (
            <section className="card assistant-welcome">
              <div className="card-head compact">
                <div>
                  <h2>Start with a question</h2>
                  <p className="muted small">Try one of these prompts, or write your own.</p>
                </div>
                <Lightbulb size={18} />
              </div>
              <div className="prompt-grid">
                {starterQuestions.map((prompt) => (
                  <button key={prompt} type="button" className="prompt-chip" onClick={() => ask(prompt)}>
                    <Sparkles size={14} /> {prompt}
                  </button>
                ))}
              </div>
            </section>
          )}

          {latest && (
            <div className="answer-stack">
              <div className="question-bubble">
                <span className="question-label">Question</span>
                <p>{latest.question}</p>
              </div>

              <section className={`card answer-card answer-${latest.answer.status.toLowerCase()}`}>
                <div className="answer-head">
                  <div>
                    <div className="answer-status-row">
                      <Badge tone={latest.answer.status === 'ANSWERED' ? 'good' : latest.answer.status === 'CLARIFY' ? 'warn' : 'danger'}>
                        {latest.answer.status}
                      </Badge>
                      <span className="muted small">{latest.answer.readOnly ? 'No records were changed' : ''}</span>
                    </div>
                    <h2>{latest.answer.summary}</h2>
                    <p className="muted">{latest.answer.interpretation}</p>
                  </div>
                  <span className="answer-icon">
                    {latest.answer.status === 'ANSWERED' ? <Table2 size={20} /> : <Lightbulb size={20} />}
                  </span>
                </div>

                {latest.answer.filters.length > 0 && (
                  <div className="filter-chips">
                    {latest.answer.filters.map((chip) => (
                      <span className="filter-chip" key={`${chip.label}-${chip.value}`}>
                        <small>{chip.label}</small> {chip.value}
                      </span>
                    ))}
                  </div>
                )}

                {latest.answer.status === 'CLARIFY' && (
                  <div className="clarify-panel">
                    <p>The parser needs one more detail before it can run safely.</p>
                    <div className="clarify-options">
                      {latest.answer.clarificationOptions.map((option) => (
                        <button key={option} type="button" className="prompt-chip" onClick={() => ask(option)}>
                          {option}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {latest.answer.status === 'ANSWERED' && latest.answer.rowCount > 0 && (
                  <div className="table-wrap answer-table-wrap">
                    <table className="data-table compact-table">
                      <thead>
                        <tr>
                          {latest.answer.columns.map((column) => (
                            <th key={column.key} className={column.type === 'number' || column.type === 'percent' ? 'number-col' : ''}>
                              {column.label}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {latest.answer.rows.map((row, index) => (
                          <tr key={index}>
                            {latest.answer.columns.map((column) => (
                              <td key={column.key} className={column.type === 'number' || column.type === 'percent' ? 'number-col' : ''}>
                                {renderCell(row[column.key], column.type)}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <div className="table-foot">
                      <span className="muted small">{latest.answer.rowCount} rows · preview limited to 50</span>
                    </div>
                  </div>
                )}

                {latest.answer.status === 'REFUSED' && (
                  <div className="inline-banner error">
                    <TriangleAlert size={17} />
                    <span>The assistant does not make changes. Ask a read-only question or use the relevant workspace screen.</span>
                  </div>
                )}

                <details className="plan-block">
                  <summary><Database size={15} /> Show query plan</summary>
                  <pre>{latest.answer.plan}</pre>
                </details>
                <p className="answer-scope">{latest.answer.scopeNote}</p>
              </section>

              {turns.length > 1 && (
                <section className="card">
                  <div className="card-head compact">
                    <h2>Earlier questions</h2>
                  </div>
                  <div className="history-list">
                    {turns.slice(0, -1).reverse().slice(0, 5).map((turn, index) => (
                      <button key={`${turn.question}-${index}`} type="button" className="history-row" onClick={() => ask(turn.question)}>
                        <span>{turn.question}</span>
                        <small>{turn.answer.rowCount} rows</small>
                      </button>
                    ))}
                  </div>
                </section>
              )}
            </div>
          )}
        </div>

        <aside className="assistant-aside">
          <section className="card">
            <div className="card-head compact">
              <h2>Try asking</h2>
              <Sparkles size={17} />
            </div>
            <div className="example-list">
              {examples.map((example) => (
                <button key={example} type="button" className="example-row" onClick={() => ask(example)}>
                  <span>{example}</span>
                  <ArrowUp size={13} className="example-arrow" />
                </button>
              ))}
            </div>
          </section>

          <section className="card guardrail-card">
            <div className="card-head compact">
              <h2>Guardrails</h2>
              <ShieldCheck size={18} />
            </div>
            <ul className="guardrail-list">
              <li>Read-only: no marks, fees, attendance or risk data is changed.</li>
              <li>Questions about individual students are limited by the signed-in scope.</li>
              <li>Write verbs are refused, never silently executed.</li>
              <li>Filters and the SQL-style plan are always visible.</li>
              <li>No external AI provider receives student records.</li>
            </ul>
          </section>

          <section className="card compact-card">
            <h2>What it covers</h2>
            <div className="coverage-row"><span>Attendance</span><span>Term, month, threshold</span></div>
            <div className="coverage-row"><span>Marks</span><span>Subject and class averages</span></div>
            <div className="coverage-row"><span>Fees</span><span>Outstanding and overdue</span></div>
            <div className="coverage-row"><span>Risk</span><span>Band and support status</span></div>
            <div className="coverage-row"><span>Consent</span><span>Guardian consent pending</span></div>
          </section>
        </aside>
      </div>
    </div>
  );
}

function renderCell(value: unknown, type: string) {
  if (value === null || value === undefined || value === '') return '—';
  if (type === 'percent') return percent(Number(value));
  if (type === 'number') return formatNumber(Number(value));
  if (type === 'money') return money(Number(value));
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  return String(value);
}
