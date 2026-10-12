import { useMemo } from "react";
import { useLocation } from "react-router-dom";

/** Maps the current Console pathname to a short header title for mobile layouts. */
export function useConsolePageTitle(): string {
  const { pathname } = useLocation();

  return useMemo(() => {
    const path = pathname.split("?")[0]?.toLowerCase() ?? "/";
    if (path === "/" || path === "/dashboard") return "Dashboard";
    if (path.startsWith("/transaction-monitoring")) return "Live monitoring";
    if (path.startsWith("/screening")) return "Screening";
    if (path.startsWith("/alerts")) return "Alerts";
    if (path.startsWith("/cases")) return "Cases";
    if (path.startsWith("/users")) return "Users";
    if (path.startsWith("/settings")) return "Settings";
    if (path.startsWith("/psps")) return "PSPs";
    if (path.startsWith("/organization")) return "My organization";
    if (path.startsWith("/billing")) return "Billing";
    if (path.startsWith("/reports")) return "Reports";
    if (path.startsWith("/regulatory-reports")) return "Regulatory reports";
    if (path.startsWith("/audit")) return "Audit logs";
    if (path.startsWith("/messages")) return "Messages";
    if (path.startsWith("/profile")) return "Profile";
    if (path.startsWith("/edge-nodes")) return "Edge nodes";
    if (path.startsWith("/merchants")) return "Merchants";
    if (path.startsWith("/kyc-documents")) return "KYC documents";
    if (path.startsWith("/rules-generation")) return "Risk rules";
    if (path.startsWith("/risk-analytics")) return "Risk analytics";
    if (path.startsWith("/chargebacks")) return "Chargebacks";
    if (path.startsWith("/network")) return "Network";
    if (path.startsWith("/records/")) return "Record detail";
    const segment = path.replace(/^\//, "").split("/")[0] ?? "Console";
    return segment.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
  }, [pathname]);
}
