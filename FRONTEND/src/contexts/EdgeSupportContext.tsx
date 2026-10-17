import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "./AuthContext";
import { isPlatformAdmin } from "../lib/userAccess";
import { EDGE_DATA_QUERY_ROOT, SUPPORT_SESSION_MS } from "../lib/edgeData";
import { EdgeSupportContext, type EdgeSupportValue, type SupportSession } from "./edgeSupport";

/**
 * Holds the platform admin's support session in memory. Ending it (or its 60-minute expiry, or
 * signing out) removes every pulled Edge result from the React Query cache.
 */
export function EdgeSupportProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isPlatform = isPlatformAdmin(user);
  const [session, setSession] = useState<SupportSession | null>(null);

  /** Drops the session and the data pulled under it. */
  const end = useCallback(() => {
    setSession(null);
    queryClient.removeQueries({ queryKey: [EDGE_DATA_QUERY_ROOT] });
  }, [queryClient]);

  /** Opens a session for one PSP; a new session replaces the previous one. */
  const start = useCallback(
    (pspId: number, pspLabel: string, reason: string) => {
      queryClient.removeQueries({ queryKey: [EDGE_DATA_QUERY_ROOT] });
      const now = Date.now();
      setSession({ pspId, pspLabel, reason: reason.trim(), startedAt: now, expiresAt: now + SUPPORT_SESSION_MS });
    },
    [queryClient],
  );

  // Expire the session after 60 minutes.
  useEffect(() => {
    if (!session) return undefined;
    const timer = window.setTimeout(end, Math.max(0, session.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [session, end]);

  // A different (or no) user never inherits a session or its data. The first render has nothing
  // to clear, and clearing it would orphan the pulls the first page is starting.
  const signedInAs = useRef(user?.username);
  useEffect(() => {
    if (signedInAs.current === user?.username) return;
    signedInAs.current = user?.username;
    setSession(null);
    queryClient.removeQueries({ queryKey: [EDGE_DATA_QUERY_ROOT] });
  }, [user?.username, queryClient]);

  const value = useMemo<EdgeSupportValue>(
    () => ({
      isPlatform,
      session: isPlatform ? session : null,
      ready: !isPlatform || session !== null,
      params: isPlatform && session ? { pspId: session.pspId, reason: session.reason } : null,
      start,
      end,
    }),
    [isPlatform, session, start, end],
  );

  return <EdgeSupportContext.Provider value={value}>{children}</EdgeSupportContext.Provider>;
}
