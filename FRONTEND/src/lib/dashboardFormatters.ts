import type { Alert } from "../types";

/** Human-readable alert headline for dashboard tables and cards. */
export function alertHeadline(alert: Alert): string {
  const type = alert.alertType?.trim();
  if (type) return type.replace(/_/g, " ");
  return `Alert ${alert.id}`;
}

/** Secondary line for alert rows — never a bare id when description is missing. */
export function alertSubline(alert: Alert): string {
  const desc = alert.description?.trim();
  if (desc) return desc;
  const parts: string[] = [];
  if (alert.transactionId) parts.push(`Transaction ${alert.transactionId}`);
  if (alert.caseId) parts.push(`Case ${alert.caseId}`);
  return parts.length ? parts.join(" · ") : "No summary on file";
}

/** Entity column label for live alert queue rows. */
export function alertEntityLabel(alert: Alert): string {
  if (alert.transactionId) return `Txn ${alert.transactionId}`;
  if (alert.caseId) return `Case ${alert.caseId}`;
  return "No entity linked";
}

/** Normalizes dashboard status chips to semantic CSS modifiers (not gold accent). */
export function caseStatusChipClass(status?: string): string {
  switch ((status ?? "").toUpperCase()) {
    case "NEW":
      return "db-chip db-chip--danger";
    case "ASSIGNED":
    case "IN_PROGRESS":
    case "PENDING_REVIEW":
    case "INVESTIGATING":
      return "db-chip db-chip--info";
    case "ESCALATED":
      return "db-chip db-chip--warning";
    case "RESOLVED":
    case "CLOSED_CLEARED":
    case "CLOSED_SAR_FILED":
    case "CLOSED_BLOCKED":
    case "CLOSED_REJECTED":
      return "db-chip db-chip--success";
    default:
      return "db-chip";
  }
}
