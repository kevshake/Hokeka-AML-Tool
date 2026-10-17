import type { Transaction } from "../types";

/** Display helpers for transactions pulled from the Edge. */

/** Amount in major units with its currency, from minor units. */
export function formatMinorAmount(minor: number | undefined | null, currency: string | undefined): string {
  if (minor == null || !Number.isFinite(minor)) return "-";
  const major = minor / 100;
  if (currency && /^[A-Z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(major);
    } catch {
      return `${major.toFixed(2)} ${currency}`;
    }
  }
  return major.toFixed(2);
}

/** The id a transaction row links to: the decision record id, else the legacy id. */
export function transactionLinkId(item: Partial<Transaction>): string | undefined {
  const id = item.edgeAssessmentId ?? item.id;
  return id === undefined || id === null ? undefined : String(id);
}
