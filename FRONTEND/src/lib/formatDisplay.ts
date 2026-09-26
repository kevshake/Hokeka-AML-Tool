/** Safe display helpers — never render NaN or the string "undefined". */

export function formatCount(value: number | null | undefined, fallback = '0'): string {
  const n = Number(value)
  return Number.isFinite(n) ? String(Math.trunc(n)) : fallback
}

export function formatCurrency(
  amount: number | null | undefined,
  currency = 'USD',
  fallback = '—',
): string {
  const n = Number(amount)
  if (!Number.isFinite(n)) return fallback
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(n)
  } catch {
    return fallback
  }
}
