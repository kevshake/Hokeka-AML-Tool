/** Alert disposition codes aligned with com.posgateway.aml.model.AlertDisposition. */
export const ALERT_DISPOSITION_OPTIONS = [
  { value: "FALSE_POSITIVE", label: "False positive", makerChecker: false },
  { value: "DUPLICATE", label: "Duplicate", makerChecker: false },
  { value: "TECHNICAL_ERROR", label: "Technical error", makerChecker: false },
  { value: "TRUE_POSITIVE_SAR_FILED", label: "True positive — SAR filed", makerChecker: true },
  { value: "TRUE_POSITIVE_BLOCKED", label: "True positive — blocked", makerChecker: true },
  { value: "TRUE_POSITIVE_REPORTED", label: "True positive — reported", makerChecker: true },
  { value: "ESCALATED", label: "Escalated", makerChecker: false },
  { value: "PENDING_INFORMATION", label: "Pending information", makerChecker: false },
  { value: "ONGOING_MONITORING", label: "Ongoing monitoring", makerChecker: false },
  { value: "CLEARED_LOW_RISK", label: "Cleared — low risk", makerChecker: false },
  { value: "MERGED_WITH_CASE", label: "Merged with case", makerChecker: false },
] as const;

export type AlertDispositionCode = (typeof ALERT_DISPOSITION_OPTIONS)[number]["value"];

/** Returns true when the disposition requires a second reviewer per Control Plane maker-checker rules. */
export function dispositionRequiresSecondReview(code: AlertDispositionCode): boolean {
  return ALERT_DISPOSITION_OPTIONS.some((o) => o.value === code && o.makerChecker);
}
