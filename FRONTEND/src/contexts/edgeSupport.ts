import { createContext, useContext } from "react";
import type { EdgeSupportParams } from "../lib/edgeData";

/**
 * Hokeka support access (contract section 9): a platform admin who opens a PSP's transaction data
 * must name the PSP and give a reason, sent with every pull as {@code X-Support-Reason}. The session
 * is held in React state only (never in browser storage) and lasts at most 60 minutes.
 */
export interface SupportSession {
  pspId: number;
  pspLabel: string;
  reason: string;
  startedAt: number;
  expiresAt: number;
}

/** What the support context exposes to pages and hooks. */
export interface EdgeSupportValue {
  /** The signed-in user is platform staff, so pulls need a support session. */
  isPlatform: boolean;
  /** The open support session, if any. */
  session: SupportSession | null;
  /** Pulls may be sent: a PSP user, or a platform admin with an open session. */
  ready: boolean;
  /** Parameters sent with every pull ({@code null} for PSP users). */
  params: EdgeSupportParams | null;
  /** Opens a support session for one PSP and ticket. */
  start: (pspId: number, pspLabel: string, reason: string) => void;
  /** Ends the session and drops every pulled result from memory. */
  end: () => void;
}

/** Default for trees without a provider (tests, PSP users): no gate. */
const NO_SUPPORT: EdgeSupportValue = {
  isPlatform: false,
  session: null,
  ready: true,
  params: null,
  start: () => undefined,
  end: () => undefined,
};

/** React context of the support session. */
export const EdgeSupportContext = createContext<EdgeSupportValue>(NO_SUPPORT);

/** The current support session context. */
export function useEdgeSupport(): EdgeSupportValue {
  return useContext(EdgeSupportContext);
}

/** Whether {@code session} is still within its 60-minute window at {@code now}. */
export function isSessionLive(session: SupportSession | null, now = Date.now()): boolean {
  return !!session && now < session.expiresAt;
}
