import { describe, expect, it } from 'vitest';
import {
  compactMoney,
  initials,
  money,
  parseDate,
  percent,
  periodLabel,
  titleCase,
} from './format';

describe('format helpers', () => {
  it('formats Indian currency without noise', () => {
    expect(money(120000)).toContain('1,20,000');
    expect(money(120000)).toContain('₹');
    expect(compactMoney(120000)).toBe('₹1.2 L');
    expect(compactMoney(12000000)).toBe('₹1.2 cr');
  });

  it('renders missing values as the shared dash', () => {
    expect(money(null)).toBe('—');
    expect(percent(undefined)).toBe('—');
  });

  it('formats percentages and trims trailing zeroes', () => {
    expect(percent(75)).toBe('75%');
    expect(percent(87.25)).toBe('87.3%');
    expect(percent(0)).toBe('0%');
  });

  it('builds initials while dropping academic titles', () => {
    expect(initials('Dr. Meera Sahoo')).toBe('MS');
    expect(initials('Sasmita Nayak')).toBe('SN');
    expect(initials('Prof. Rakesh Das')).toBe('RD');
  });

  it('title-cases enum values', () => {
    expect(titleCase('at_risk')).toBe('At Risk');
    expect(titleCase('OVERDUE')).toBe('Overdue');
  });

  it('parses date-only values without a timezone shift', () => {
    const date = parseDate('2026-01-01');
    expect(date.getFullYear()).toBe(2026);
    expect(date.getMonth()).toBe(0);
    expect(date.getDate()).toBe(1);
  });

  it('labels class periods with a stable start time', () => {
    expect(periodLabel(1)).toContain('Period 1');
    expect(periodLabel(8)).toContain('4:25');
  });
});
