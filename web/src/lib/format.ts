const moneyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

const numberFormatter = new Intl.NumberFormat('en-IN');

export function money(value: number | null | undefined, decimals = 0): string {
  if (value === null || value === undefined) return '—';
  if (decimals > 0) {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
  }
  return moneyFormatter.format(value);
}

export function compactMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  if (Math.abs(value) >= 10_000_000) return `₹${(value / 10_000_000).toFixed(1)} cr`;
  if (Math.abs(value) >= 100_000) return `₹${(value / 100_000).toFixed(1)} L`;
  return money(value);
}

export function number(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return numberFormatter.format(value);
}

export function percent(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined) return '—';
  return `${Number(value).toFixed(digits).replace(/\.0$/, '')}%`;
}

export function parseDate(date: string): Date {
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    const [year, month, day] = date.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  return new Date(date);
}

export function shortDate(date: string | null | undefined): string {
  if (!date) return '—';
  return parseDate(date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}

export function longDate(date: string | null | undefined): string {
  if (!date) return '—';
  return parseDate(date).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

export function timeOnly(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

export function relativeTime(value: string | null | undefined): string {
  if (!value) return 'Not yet';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  const diff = Date.now() - date.getTime();
  const minutes = Math.round(diff / 60_000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days} day${days === 1 ? '' : 's'} ago`;
  return shortDate(value);
}

export function initials(name: string): string {
  return name
    .replace(/^(Dr\.|Prof\.|Mr\.|Ms\.|Mrs\.)\s+/i, '')
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split(/[\s_]+/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function periodLabel(period: number): string {
  const start = [
    '9:00',
    '9:55',
    '11:00',
    '11:55',
    '1:30',
    '2:25',
    '3:30',
    '4:25',
  ][Math.min(Math.max(period - 1, 0), 7)];
  return `Period ${period} · ${start}`;
}
