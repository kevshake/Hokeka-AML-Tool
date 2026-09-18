import { formatDistanceToNowStrict } from 'date-fns';
import type { Psp } from '../../types';
import type { EdgeHealth, EdgeNodeStatus } from './types';

/** Best available human label for a PSP across the several id/name shapes in use. */
export function pspLabel(p: Psp): string {
  return p.legalName || p.tradingName || p.name || p.code || p.pspCode || `PSP ${p.id ?? ''}`;
}

/** Monospace stack for codes, commands and fingerprints (mirrors tokens.fonts.mono). */
export const MONO_FONT = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace";

/** MUI palette slots — each is wired to a shared token in ThemeContext's MuiChip theme. */
export type MuiColor =
  | 'default'
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'error'
  | 'info';

/* -------------------------------------------------------------------------- */
/* Status → chip identity (UX spec §3 state-presentation table)               */
/* -------------------------------------------------------------------------- */

interface StatusMeta {
  label: string;
  /** Token-backed chip slot: PENDING neutral, APPROVED gold, ACTIVE teal,
   *  SUSPENDED amber, REVOKED/REJECTED red. */
  color: MuiColor;
}

const STATUS_META: Record<EdgeNodeStatus, StatusMeta> = {
  PENDING: { label: 'Pending', color: 'default' },
  APPROVED: { label: 'Approved', color: 'primary' },
  ACTIVE: { label: 'Active', color: 'secondary' },
  SUSPENDED: { label: 'Suspended', color: 'warning' },
  REVOKED: { label: 'Revoked', color: 'error' },
  REJECTED: { label: 'Rejected', color: 'error' },
};

export function statusMeta(status: EdgeNodeStatus | string): StatusMeta {
  return STATUS_META[status as EdgeNodeStatus] ?? { label: status, color: 'default' };
}

/** The one-line meaning shown next to the chip (UX spec §3). */
export function statusBlurb(
  status: EdgeNodeStatus | string,
  opts: { lastSeenAt?: string | null; enrollmentCodeExpiresAt?: string | null; healthy?: boolean },
): string {
  switch (status) {
    case 'PENDING':
      return 'Awaiting Hokeka approval';
    case 'APPROVED': {
      const hrs = hoursUntil(opts.enrollmentCodeExpiresAt);
      return hrs != null ? `Ready to install — code expires in ${hrs}h` : 'Ready to install';
    }
    case 'ACTIVE':
      return opts.lastSeenAt
        ? `${opts.healthy ? 'Healthy' : 'Last seen'} · ${formatRelative(opts.lastSeenAt)}`
        : 'Active — not seen yet';
    case 'SUSPENDED':
      return 'Paused — not receiving rules';
    case 'REVOKED':
      return 'Revoked — re-enrollment required';
    case 'REJECTED':
      return 'Request rejected';
    default:
      return '';
  }
}

/* -------------------------------------------------------------------------- */
/* Health → chip identity                                                     */
/* -------------------------------------------------------------------------- */

const HEALTH_META: Record<EdgeHealth, StatusMeta> = {
  HEALTHY: { label: 'Healthy', color: 'success' },
  STALE: { label: 'Stale', color: 'warning' },
  NEVER_SEEN: { label: 'Never seen', color: 'info' },
  AWAITING_APPROVAL: { label: 'Awaiting approval', color: 'default' },
  AWAITING_ACTIVATION: { label: 'Awaiting activation', color: 'primary' },
  SUSPENDED: { label: 'Suspended', color: 'warning' },
  REVOKED: { label: 'Revoked', color: 'error' },
  REJECTED: { label: 'Rejected', color: 'error' },
};

export function healthMeta(health: EdgeHealth | string): StatusMeta {
  return HEALTH_META[health as EdgeHealth] ?? { label: health, color: 'default' };
}

/* -------------------------------------------------------------------------- */
/* Formatting                                                                 */
/* -------------------------------------------------------------------------- */

export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return formatDistanceToNowStrict(d, { addSuffix: true });
}

export function formatAbsolute(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString();
}

/** Whole hours from now until `iso` (never negative). */
export function hoursUntil(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime() - Date.now();
  if (Number.isNaN(ms)) return null;
  return Math.max(0, Math.round(ms / 3_600_000));
}

/** Microsecond latency → a compact µs/ms label. */
export function formatMicros(micros: number | null | undefined): string {
  if (micros == null || Number.isNaN(micros)) return '—';
  if (micros >= 1000) return `${(micros / 1000).toLocaleString(undefined, { maximumFractionDigits: 2 })} ms`;
  return `${Math.round(micros).toLocaleString()} µs`;
}

export function formatCount(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—';
  return n.toLocaleString();
}

/** Distinct rules that produced a hit in a window (`{ruleId: count}` JSON). */
export function countRuleHits(json: string | null | undefined): number | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return Object.keys(parsed).length;
  } catch {
    return null;
  }
}

/* -------------------------------------------------------------------------- */
/* Role gating                                                                */
/* -------------------------------------------------------------------------- */

const PLATFORM_ROLES = new Set(['SUPER_ADMIN', 'ADMIN', 'PLATFORM_ADMIN']);

/** Platform admins (Hokeka) may approve/reject and see every PSP's fleet. */
export function isPlatformAdmin(roleName: string | null | undefined): boolean {
  return !!roleName && PLATFORM_ROLES.has(roleName.toUpperCase());
}

/* -------------------------------------------------------------------------- */
/* Setup-command helpers                                                      */
/* -------------------------------------------------------------------------- */

/** A stable, operator-friendly default edge id derived from the display name. */
export function slugifyEdgeId(displayName: string): string {
  return (
    displayName
      .toLowerCase()
      .normalize('NFKD')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 48) || 'edge-node'
  );
}

export type InstallPlatform = 'linux' | 'windows';

export interface InstallParams {
  pspId: number | string;
  edgeId: string;
  enrollmentCode: string;
  controlPlaneUrl: string;
}

/** The copy-ready installer invocation (UX spec §4 step 3; matches edge-host/deploy). */
export function installCommand(platform: InstallPlatform, p: InstallParams): string {
  const psp = String(p.pspId);
  if (platform === 'windows') {
    return `.\\install.ps1 -PspId ${psp} -EdgeId ${p.edgeId} -EnrollmentCode ${p.enrollmentCode} -ControlPlane ${p.controlPlaneUrl}`;
  }
  return `sudo ./install.sh --pspid ${psp} --edgeid ${p.edgeId} --enrollment-code ${p.enrollmentCode} --controlplane ${p.controlPlaneUrl}`;
}

/* -------------------------------------------------------------------------- */
/* Key fingerprints (UX spec §5 — render a short SHA-256, never the raw blob)  */
/* -------------------------------------------------------------------------- */

// Return type is intentionally inferred as Uint8Array<ArrayBuffer> (not the wider
// ArrayBufferLike) so crypto.subtle.digest accepts it as a BufferSource.
function base64ToBytes(b64: string) {
  const bin = atob(b64);
  const buffer = new ArrayBuffer(bin.length);
  const out = new Uint8Array(buffer);
  for (let i = 0; i < bin.length; i += 1) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToBase64(bytes: Uint8Array): string {
  let bin = '';
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  return btoa(bin);
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export interface Fingerprint {
  /** SSH-style short form, e.g. `SHA256:q1w2…`. */
  short: string;
  /** Full colon-grouped hex, for the tooltip / out-of-band read-back. */
  hex: string;
}

/**
 * SHA-256 over the DECODED public-key bytes, rendered as a compact fingerprint.
 * Throws if the key is not valid base64 or Web Crypto is unavailable — the caller
 * surfaces that as an explicit error state rather than a neutral blank (UX spec §5).
 */
export async function keyFingerprint(base64Key: string): Promise<Fingerprint> {
  if (!crypto?.subtle) {
    throw new Error('Web Crypto unavailable (page is not in a secure context)');
  }
  const bytes = base64ToBytes(base64Key.trim());
  const digestBuf = await crypto.subtle.digest('SHA-256', bytes);
  const digest = new Uint8Array(digestBuf);
  const hexPairs = bytesToHex(digest).match(/.{2}/g) ?? [];
  return {
    short: `SHA256:${bytesToBase64(digest).replace(/=+$/, '')}`,
    hex: hexPairs.join(':').toUpperCase(),
  };
}
