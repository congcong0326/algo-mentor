export function isoDateToday(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export function isoDateDaysAgo(days: number): string {
  const now = new Date();
  now.setDate(now.getDate() - days);
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

export function formatNumber(value: number | null | undefined): string {
  return new Intl.NumberFormat().format(value ?? 0);
}

/** Decimal values are deliberately rendered rather than converted to a JS number. */
export function formatEstimatedUsd(value: string | null | undefined): string {
  return `USD ${value?.trim() || '0.00000000'}`;
}

export function formatDateTime(value: string | null | undefined, fallback = '—'): string {
  if (!value) {
    return fallback;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return [
    date.getUTCFullYear(),
    String(date.getUTCMonth() + 1).padStart(2, '0'),
    String(date.getUTCDate()).padStart(2, '0'),
  ].join('-') + ` ${String(date.getUTCHours()).padStart(2, '0')}:${String(date.getUTCMinutes()).padStart(2, '0')}`;
}

export function isWholeLimit(value: string): boolean {
  const parsed = Number(value);
  return /^\d+$/.test(value.trim()) && Number.isInteger(parsed) && parsed >= 1 && parsed <= 10_000;
}

export function isNonNegativeDecimal(value: string): boolean {
  return /^\d+(?:\.\d+)?$/.test(value.trim());
}

export function isPositiveDecimal(value: string): boolean {
  if (!isNonNegativeDecimal(value)) {
    return false;
  }
  return /[1-9]/.test(value.replace('.', ''));
}
