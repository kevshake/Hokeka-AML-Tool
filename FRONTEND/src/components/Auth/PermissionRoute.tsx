import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { canAccessRoute } from "../../lib/consoleRbac";
import HokekaPageShell from "../Layout/HokekaPageShell";
import GlassCard from "../Common/GlassCard";

interface PermissionRouteProps {
  children: React.ReactNode;
}

/** Redirects or shows an access-denied state when the user lacks route permissions. */
export default function PermissionRoute({ children }: PermissionRouteProps) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (!canAccessRoute(user, location.pathname)) {
    return (
      <HokekaPageShell title="Access restricted" subtitle="Your role cannot open this workspace area">
        <GlassCard padding="lg" static>
          <p className="text-sm text-glass-muted">
            This page requires permissions that are not granted to your account (
            {user.role?.name ?? "unknown role"}). Contact your platform or PSP administrator if you
            need access.
          </p>
        </GlassCard>
      </HokekaPageShell>
    );
  }

  return <>{children}</>;
}
