import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  BellRing,
  CheckCircle2,
  Download,
  IndianRupee,
  Loader2,
  MessageSquareText,
  ReceiptText,
  Search,
  Wallet,
} from 'lucide-react';
import { api, ApiError } from '../lib/api';
import { shortDate, money, compactMoney, dateTime } from '../lib/format';
import { useToast } from '../lib/toast';
import type { FeeRow } from '../lib/types';
import { Avatar, Badge, ErrorState, Field, Modal, PageHeader, ProgressBar, Spinner, StatCard } from '../components/ui';

type ReminderResult = { count: number; queuedAt: string; messageEnglish: string; messageOdia: string };

export function FeesPage() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [search, setSearch] = useState('');
  const [programmeId, setProgrammeId] = useState<number | ''>('');
  const [status, setStatus] = useState('');
  const [collectFor, setCollectFor] = useState<FeeRow | null>(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<'UPI' | 'CARD' | 'NETBANKING'>('UPI');
  const [paymentKey, setPaymentKey] = useState('');
  const [payBusy, setPayBusy] = useState(false);
  const [payError, setPayError] = useState('');
  const [receipt, setReceipt] = useState<{ receiptNo: string; amount: number; method: string; paidAt: string; studentName: string; rollNo: string; duplicate: boolean; studentId: number } | null>(null);
  const [reminder, setReminder] = useState<ReminderResult | null>(null);
  const [reminderBusy, setReminderBusy] = useState(false);

  const metaQuery = useQuery({ queryKey: ['meta'], queryFn: api.meta });
  const feesQuery = useQuery({
    queryKey: ['fees', search, programmeId, status],
    queryFn: () => api.fees.list({ query: search, programmeId: programmeId || undefined, status }),
  });

  const data = feesQuery.data;
  const rows = data?.rows ?? [];
  const programmes = metaQuery.data?.programmes ?? [];

  const dueRows = useMemo(() => rows.filter((row) => row.dueAmount > 0), [rows]);

  if (feesQuery.isLoading) return <Spinner label="Opening the fee desk" />;
  if (feesQuery.isError || !data) {
    return <ErrorState message={(feesQuery.error as Error)?.message ?? 'Fee records could not be loaded.'} onRetry={() => feesQuery.refetch()} />;
  }

  const openCollect = (row: FeeRow) => {
    setCollectFor(row);
    setAmount(String(row.dueAmount));
    setMethod('UPI');
    setPaymentKey(crypto.randomUUID());
    setPayError('');
  };

  const pay = async () => {
    if (!collectFor) return;
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setPayError('Enter a payment amount above zero.');
      return;
    }
    if (value > collectFor.dueAmount) {
      setPayError(`Payment cannot exceed the outstanding ${money(collectFor.dueAmount)}.`);
      return;
    }
    setPayBusy(true);
    setPayError('');
    try {
      const payment = await api.fees.pay(collectFor.studentId, { amount: value, method, idempotencyKey: paymentKey });
      setReceipt({
        receiptNo: payment.receiptNo,
        amount: payment.amount,
        method: payment.method,
        paidAt: payment.paidAt,
        studentName: payment.studentName,
        rollNo: payment.rollNo,
        duplicate: payment.duplicate,
        studentId: collectFor.studentId,
      });
      setCollectFor(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['fees'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
        queryClient.invalidateQueries({ queryKey: ['student'] }),
      ]);
      notify(payment.duplicate ? 'This payment was already recorded.' : 'Payment recorded in test mode.');
    } catch (error) {
      setPayError(error instanceof ApiError ? error.message : 'The payment could not be recorded.');
    } finally {
      setPayBusy(false);
    }
  };

  const sendReminders = async () => {
    setReminderBusy(true);
    try {
      const result = await api.fees.reminders();
      setReminder(result);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['fees'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
      notify(`${result.count} reminders queued in mock SMS mode.`);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'Reminders could not be queued.', 'error');
    } finally {
      setReminderBusy(false);
    }
  };

  return (
    <div>
      <PageHeader
        eyebrow={`Financial desk · ${data.summary.academicYear}`}
        title="Fees"
        description="Follow up on outstanding dues, record a test payment, and export a Tally-ready ledger."
        actions={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => void sendReminders()} disabled={reminderBusy}>
              {reminderBusy ? <Loader2 size={16} className="spin" /> : <BellRing size={16} />}
              {reminderBusy ? 'Queuing' : 'Send reminders'}
            </button>
            <button type="button" className="btn btn-primary" onClick={() => void api.fees.downloadTally()}>
              <Download size={16} /> Tally export
            </button>
          </>
        }
      />

      <section className="stat-grid four">
        <StatCard label="Net billed" value={compactMoney(data.summary.totalBilled)} hint={`${data.summary.defaulters} students with outstanding dues`} icon={<ReceiptText size={18} />} tone="brand" />
        <StatCard label="Collected" value={compactMoney(data.summary.totalCollected)} hint={`${data.summary.collectionRatePercent}% collection rate`} icon={<CheckCircle2 size={18} />} tone="good" />
        <StatCard label="Outstanding" value={compactMoney(data.summary.totalOutstanding)} hint={`${data.summary.reminderCandidates} reminder candidates`} icon={<Wallet size={18} />} tone={data.summary.totalOutstanding > 0 ? 'warn' : 'good'} />
        <StatCard label="Last reminder run" value={dateTime(data.summary.lastReminderAt)} hint="Mock SMS mode · English + Odia parent copy" icon={<BellRing size={18} />} />
      </section>

      <section className="card">
        <div className="fee-toolbar">
          <div className="search-field">
            <Search size={16} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name or roll number"
              aria-label="Search fee records"
            />
          </div>
          <div className="filter-row">
            <select value={programmeId} onChange={(event) => setProgrammeId(event.target.value ? Number(event.target.value) : '')} aria-label="Filter by programme">
              <option value="">All programmes</option>
              {programmes.map((programme) => (
                <option key={programme.id} value={programme.id}>{programme.name}</option>
              ))}
            </select>
            <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter by fee status">
              <option value="">All statuses</option>
              <option value="DUE">Due</option>
              <option value="OVERDUE">Overdue</option>
              <option value="CLEARED">Cleared</option>
            </select>
            <span className="muted small">{rows.length} of {data.rows.length} records</span>
          </div>
        </div>

        {rows.length === 0 ? (
          <div className="quiet-block">
            <Search size={20} />
            <p>No fee records match these filters.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Programme</th>
                  <th className="number-col">Billed</th>
                  <th className="number-col">Paid</th>
                  <th className="number-col">Due</th>
                  <th>Status</th>
                  <th>Due date</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.accountId}>
                    <td>
                      <div className="table-student">
                        <Avatar name={row.studentName} size="sm" />
                        <span>
                          <Link to={`/students/${row.studentId}`}>{row.studentName}</Link>
                          <small>{row.rollNo} · {row.category}</small>
                        </span>
                      </div>
                    </td>
                    <td><span className="muted">{row.programmeCode}</span><br /><small className="muted">Year {row.studyYear}</small></td>
                    <td className="number-col">{money(row.grossAmount - row.scholarshipAmount)}</td>
                    <td className="number-col">{money(row.paidAmount)}</td>
                    <td className="number-col"><strong className={row.dueAmount > 0 ? 'text-danger' : ''}>{money(row.dueAmount)}</strong></td>
                    <td>
                      <Badge tone={row.status === 'CLEARED' ? 'good' : row.status === 'OVERDUE' ? 'danger' : 'warn'}>
                        {row.status}{row.daysOverdue > 0 ? ` · ${row.daysOverdue}d` : ''}
                      </Badge>
                    </td>
                    <td><span className="muted">{shortDate(row.dueDate)}</span></td>
                    <td className="action-col">
                      {row.dueAmount > 0 ? (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => openCollect(row)}>
                          <IndianRupee size={14} /> Collect
                        </button>
                      ) : (
                        <span className="muted small">Cleared</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="table-foot">
          <span className="muted small">Showing {rows.length} records · Test-mode checkout only; no money moves.</span>
          {dueRows.length > 0 && <span className="muted small">{money(dueRows.reduce((sum, row) => sum + row.dueAmount, 0))} in filtered dues</span>}
        </div>
      </section>

      <div className="fees-lower">
        <section className="card">
          <div className="card-head compact">
            <div>
              <h2>Recent receipts</h2>
              <p className="muted small">Latest collections across all programmes.</p>
            </div>
            <ReceiptText size={18} />
          </div>
          {data.recentPayments.length === 0 ? (
            <p className="muted small">No payments recorded yet.</p>
          ) : (
            <div className="receipt-list">
              {data.recentPayments.map((payment) => (
                <Link to={`/students/${payment.studentId}`} className="receipt-row" key={payment.paymentId}>
                  <span className="receipt-icon"><IndianRupee size={15} /></span>
                  <span className="class-copy">
                    <strong>{payment.studentName}</strong>
                    <small>{payment.receiptNo} · {payment.method} · {dateTime(payment.paidAt)}</small>
                  </span>
                  <span className="amount-paid">{money(payment.amount)}</span>
                </Link>
              ))}
            </div>
          )}
        </section>

        <section className="card automation-card">
          <div className="card-head compact">
            <div>
              <h2>Follow-up automation</h2>
              <p className="muted small">Reminder copy is generated in English and Odia.</p>
            </div>
            <MessageSquareText size={18} />
          </div>
          <div className="automation-stat">
            <strong>{data.summary.reminderCandidates}</strong>
            <span>students are ready for a gentle reminder today.</span>
          </div>
          <ProgressBar value={data.summary.collectionRatePercent} tone={data.summary.collectionRatePercent >= 70 ? 'good' : 'warn'} />
          <div className="quick-read-row"><span>Collection rate</span><strong>{data.summary.collectionRatePercent}%</strong></div>
          <div className="quick-read-row"><span>Last reminder run</span><strong>{dateTime(data.summary.lastReminderAt)}</strong></div>
          <button type="button" className="btn btn-secondary btn-block" onClick={() => void sendReminders()} disabled={reminderBusy}>
            <BellRing size={16} /> Queue reminders now
          </button>
        </section>
      </div>

      <Modal
        open={collectFor !== null}
        title={collectFor ? `Collect fee · ${collectFor.studentName}` : 'Collect fee'}
        description="This is a test-mode checkout. A receipt and audit trail are created immediately."
        onClose={() => setCollectFor(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setCollectFor(null)}>Cancel</button>
            <button type="button" className="btn btn-primary" onClick={() => void pay()} disabled={payBusy}>
              {payBusy ? <Loader2 size={15} className="spin" /> : <IndianRupee size={15} />}
              {payBusy ? 'Recording' : `Record ${money(Number(amount) || 0)}`}
            </button>
          </>
        }
      >
        {collectFor && (
          <>
            <div className="modal-summary">
              <div><span>Outstanding</span><strong>{money(collectFor.dueAmount)}</strong></div>
              <div><span>Programme</span><strong>{collectFor.programmeCode}</strong></div>
              <div><span>Due date</span><strong>{shortDate(collectFor.dueDate)}</strong></div>
            </div>
            <div className="form-grid">
              <Field label="Amount" hint={`Cannot exceed ${money(collectFor.dueAmount)}.`}>
                <div className="input-prefix">
                  <span>₹</span>
                  <input
                    type="number"
                    min="1"
                    max={collectFor.dueAmount}
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                  />
                </div>
              </Field>
              <Field label="Payment method">
                <select value={method} onChange={(event) => setMethod(event.target.value as typeof method)}>
                  <option value="UPI">UPI</option>
                  <option value="CARD">Card</option>
                  <option value="NETBANKING">Net banking</option>
                </select>
              </Field>
            </div>
            <p className="field-hint">Idempotency key: <code>{paymentKey.slice(0, 18)}…</code> · safe to retry without double-charging.</p>
            {payError && <div className="form-error">{payError}</div>}
          </>
        )}
      </Modal>

      <Modal
        open={receipt !== null}
        title="Payment receipt"
        description="Saved in the demo ledger. This is not a real financial transaction."
        onClose={() => setReceipt(null)}
        footer={<button type="button" className="btn btn-primary" onClick={() => setReceipt(null)}>Done</button>}
      >
        {receipt && (
          <div className="receipt-sheet">
            <span className="receipt-check"><CheckCircle2 size={26} /></span>
            <p className="eyebrow">CampusOne · test receipt</p>
            <h2>{money(receipt.amount)}</h2>
            <p className="muted">{receipt.studentName} · {receipt.rollNo}</p>
            <div className="receipt-rows">
              <div><span>Receipt no.</span><strong>{receipt.receiptNo}</strong></div>
              <div><span>Method</span><strong>{receipt.method}</strong></div>
              <div><span>Recorded</span><strong>{dateTime(receipt.paidAt)}</strong></div>
              <div><span>Status</span><strong>{receipt.duplicate ? 'Existing payment' : 'Success'}</strong></div>
            </div>
            <Link to={`/students/${receipt.studentId}`} className="text-link" onClick={() => setReceipt(null)}>
              Open student account <ArrowRight size={14} />
            </Link>
          </div>
        )}
      </Modal>

      <Modal
        open={reminder !== null}
        title="Reminders queued"
        description="Mock SMS mode. No messages leave this demo environment."
        onClose={() => setReminder(null)}
        footer={<button type="button" className="btn btn-primary" onClick={() => setReminder(null)}>Close</button>}
      >
        {reminder && (
          <div className="reminder-preview">
            <div className="reminder-count"><BellRing size={20} /><strong>{reminder.count}</strong> parent reminders queued at {dateTime(reminder.queuedAt)}</div>
            <div className="message-preview">
              <span className="message-label">English</span>
              <p>{reminder.messageEnglish}</p>
            </div>
            <div className="message-preview">
              <span className="message-label">Odia</span>
              <p lang="or">{reminder.messageOdia}</p>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
