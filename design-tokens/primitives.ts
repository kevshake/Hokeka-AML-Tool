import { softSurface } from "./colorMath";

/** Core palette — warm dark editorial ground, gold/teal accents (Console source of truth). */
export const colors = {
  bg: "#080909",
  bg2: "#0c0e0d",
  surface0: "#080909",
  surface1: "#0c0e0d",
  surface2: "#121514",
  surface3: "#191c1b",
  surface4: "#232624",
  ink: "#f5f2eb",
  muted: "#a8aba8",
  muted2: "#858986",
  gold: "#d3b371",
  goldBright: "#e1c684",
  goldDeep: "#74511e",
  teal: "#75b7ab",
  amber: "#e2b25d",
  line: "rgba(255, 255, 255, 0.12)",
  lineStrong: "rgba(255, 255, 255, 0.22)",
  lineControl: "rgba(255, 255, 255, 0.34)",
} as const;

export const semantic = {
  success: "#75b7ab",
  successSoft: softSurface("#75b7ab"),
  warning: "#e2b25d",
  warningSoft: softSurface("#e2b25d"),
  error: "#e8776b",
  errorSoft: softSurface("#e8776b"),
  info: "#84a9c4",
  infoSoft: softSurface("#84a9c4"),
  neutral: "#a8aba8",
  neutralSoft: softSurface("#a8aba8"),
} as const;

export const risk = {
  critical: "#e8776b",
  high: "#e08a4f",
  medium: "#e2b25d",
  low: "#75b7ab",
  unknown: "#a0a4a1",
} as const;

export const riskSoft = {
  critical: softSurface(risk.critical),
  high: softSurface(risk.high),
  medium: softSurface(risk.medium),
  low: softSurface(risk.low),
  unknown: softSurface(risk.unknown),
} as const;

export const decision = {
  ALLOW: "#75b7ab",
  ALERT: "#e2b25d",
  REVIEW: "#d3b371",
  HOLD: "#e08a4f",
  BLOCK: "#e8776b",
} as const;

export const radii = {
  xs: "2px",
  base: "3px",
  md: "4px",
  lg: "5px",
  xl: "6px",
  xxl: "8px",
  pill: "9999px",
} as const;

export const motion = {
  ease: "cubic-bezier(.22, 1, .36, 1)",
  fast: "150ms",
  base: "250ms",
  slow: "400ms",
} as const;

export const shadows = {
  none: "none",
  xs: "0 1px 2px rgba(0, 0, 0, 0.45)",
  sm: "0 10px 30px -18px rgba(0, 0, 0, 0.9)",
  md: "0 26px 50px -30px rgba(0, 0, 0, 0.75)",
  lg: "0 24px 60px -24px rgba(0, 0, 0, 0.85)",
  xl: "0 40px 100px -30px rgba(0, 0, 0, 0.72)",
  gold: "0 14px 34px -12px rgba(211, 179, 113, 0.6)",
  teal: "0 14px 34px -14px rgba(117, 183, 171, 0.5)",
} as const;

export const fonts = {
  display: "'Manrope', 'Inter', system-ui, sans-serif",
  body: "'DM Sans', 'Inter', system-ui, sans-serif",
  mono: "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace",
} as const;

export const typeScale = {
  h1: { size: "2.75rem", weight: 600, lineHeight: 1.05, letterSpacing: "-0.02em" },
  h2: { size: "2.125rem", weight: 600, lineHeight: 1.08, letterSpacing: "-0.018em" },
  h3: { size: "1.6875rem", weight: 500, lineHeight: 1.15, letterSpacing: "-0.015em" },
  h4: { size: "1.375rem", weight: 500, lineHeight: 1.22, letterSpacing: "-0.012em" },
  h5: { size: "1.125rem", weight: 600, lineHeight: 1.3, letterSpacing: "-0.01em" },
  h6: { size: "0.9375rem", weight: 600, lineHeight: 1.35, letterSpacing: "-0.006em" },
  body1: { size: "0.9063rem", weight: 400, lineHeight: 1.65, letterSpacing: "0" },
  body2: { size: "0.8125rem", weight: 400, lineHeight: 1.6, letterSpacing: "0" },
  caption: { size: "0.7188rem", weight: 400, lineHeight: 1.5, letterSpacing: "0.01em" },
  overline: { size: "0.6875rem", weight: 600, lineHeight: 1.4, letterSpacing: "0.16em" },
  button: { size: "0.8125rem", weight: 600, lineHeight: 1.2, letterSpacing: "0.01em" },
} as const;

/** 4px grid spacing scale (rem). */
export const spacing = {
  0: "0",
  1: "0.25rem",
  2: "0.5rem",
  3: "0.75rem",
  4: "1rem",
  5: "1.25rem",
  6: "1.5rem",
  8: "2rem",
  10: "2.5rem",
  12: "3rem",
  16: "4rem",
} as const;

export const DEFAULT_PRIMARY = colors.gold;
export const DEFAULT_SECONDARY = colors.teal;
