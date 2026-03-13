import { Box, Typography, Alert, Paper, CircularProgress } from "@mui/material";
import { Routes, Route, Navigate } from "react-router-dom";
import { useState, useEffect, useMemo } from "react";
import TabNavigation from "../../components/Common/TabNavigation";
import { useCurrentUser, useGrafanaDashboards } from "../../features/api/queries";

// Grafana base URL - should be configured via environment variable
const GRAFANA_BASE_URL = import.meta.env.VITE_GRAFANA_URL || "http://localhost:3000";

interface GrafanaDashboardProps {
  dashboardId: string;
  title: string;
  pspCode?: string;
}

function GrafanaDashboard({ dashboardId, title, pspCode }: GrafanaDashboardProps) {
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string>("");

  // Build iframe URL with PSP filtering and kiosk mode
  let iframeUrl = `${GRAFANA_BASE_URL}/d/${dashboardId}?orgId=1&kiosk&theme=light`;

  // Add PSP filter if user has a PSP (non-system user)
  if (pspCode) {
    iframeUrl += `&var-psp=${pspCode}`;
  }

  // Reset loading state when dashboard changes
  useEffect(() => {
    setIsLoading(true);
    setHasError(false);
  }, [dashboardId]);

  const handleIframeLoad = () => {
    setIsLoading(false);
    setHasError(false);
  };

  const handleIframeError = () => {
    setIsLoading(false);
    setHasError(true);
    setErrorMessage(
      `Failed to load dashboard "${title}". ` +
      `Please check that the dashboard ID "${dashboardId}" exists in Grafana ` +
      `and that Grafana allows iframe embedding.`
    );
  };

  return (
    <Box>
      {hasError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {errorMessage}
          <Box sx={{ mt: 1 }}>
            <Typography variant="body2">
              <strong>Troubleshooting:</strong>
            </Typography>
            <Typography variant="body2" component="ul" sx={{ mt: 0.5, pl: 2 }}>
              <li>Ensure Grafana is running at {GRAFANA_BASE_URL}</li>
              <li>Check that Grafana allows iframe embedding (configure X-Frame-Options)</li>
              <li>Verify the dashboard ID "{dashboardId}" exists in Grafana</li>
              <li>Check browser console for CORS or network errors</li>
            </Typography>
          </Box>
        </Alert>
      )}

      <Paper
        sx={{
          backgroundColor: "background.paper",
          border: "1px solid rgba(0,0,0,0.1)",
          borderRadius: 2,
          overflow: "hidden",
          height: "calc(100vh - 400px)",
          minHeight: "600px",
          position: "relative",
        }}
      >
        {isLoading && !hasError && (
          <Box
            sx={{
              position: "absolute",
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              backgroundColor: "rgba(255, 255, 255, 0.95)",
              zIndex: 1,
            }}
          >
            <Box sx={{ textAlign: "center" }}>
              <CircularProgress size={60} sx={{ color: "#8B4049", mb: 2 }} />
              <Typography variant="body1" sx={{ color: "text.secondary" }}>
                Loading {title}...
              </Typography>
            </Box>
          </Box>
        )}
        <iframe
          src={iframeUrl}
          width="100%"
          height="100%"
          frameBorder="0"
          title={title}
          style={{ display: "block" }}
          onLoad={handleIframeLoad}
          onError={handleIframeError}
          allow="fullscreen"
        />
      </Paper>
    </Box>
  );
}

export default function DashboardPage() {
  const { data: user, isLoading: isLoadingUser } = useCurrentUser();
  const { data: dashboards, isLoading: isLoadingDashboards } = useGrafanaDashboards();

  // Check if user is system admin (no PSP assigned and has MANAGE_USERS or CONFIGURE_SYSTEM permission)
  const isSystemAdmin = user && !user.psp && user.role?.permissions?.some(
    (p) => p === "MANAGE_USERS" || p === "CONFIGURE_SYSTEM" || p === "MANAGE_PSP"
  );

  // Get PSP code for filtering
  const pspCode = user?.psp?.code;

  // Convert dashboard path to route value (e.g., "/dashboard/transactions" -> "transactions")
  const pathToValue = (path: string): string => {
    return path.replace("/dashboard/", "");
  };

  // Build tabs from API response
  const tabs = useMemo(() => {
    if (!dashboards) return [];
    
    return dashboards.map((dashboard) => ({
      label: dashboard.menu,
      value: pathToValue(dashboard.path),
      path: dashboard.path,
      systemOnly: dashboard.systemOnly,
      uid: dashboard.uid, // Store UID for iframe URL
    }));
  }, [dashboards]);

  // Create a map of dashboard UIDs by path for easy lookup
  const dashboardMap = useMemo(() => {
    if (!dashboards) return new Map<string, string>();
    const map = new Map<string, string>();
    dashboards.forEach((dashboard) => {
      const value = pathToValue(dashboard.path);
      map.set(value, dashboard.uid);
    });
    return map;
  }, [dashboards]);

  const isLoading = isLoadingUser || isLoadingDashboards;

  return (
    <Box>
      <Typography variant="h4" sx={{ color: "text.primary", mb: 2, fontWeight: 600 }}>
        Dashboard
      </Typography>

      <TabNavigation tabs={tabs} />

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "400px" }}>
          <Box sx={{ textAlign: "center" }}>
            <CircularProgress size={60} sx={{ color: "#8B4049", mb: 2 }} />
            <Typography variant="body1" sx={{ color: "text.secondary" }}>
              Loading dashboard...
            </Typography>
          </Box>
        </Box>
      ) : !user ? (
        <Box sx={{ p: 3 }}>
          <Alert severity="error">Unable to load user information. Please refresh the page.</Alert>
        </Box>
      ) : !dashboards || dashboards.length === 0 ? (
        <Box sx={{ p: 3 }}>
          <Alert severity="warning">No dashboards available. Please contact your administrator.</Alert>
        </Box>
      ) : (
        <Routes>
          <Route 
            path="/" 
            element={
              <Navigate 
                to={tabs.length > 0 ? tabs[0].path : "/dashboard/transactions"} 
                replace 
              />
            } 
          />
          {tabs.map((tab) => {
            const dashboardUid = dashboardMap.get(tab.value);
            if (!dashboardUid) return null;
            
            return (
              <Route
                key={tab.value}
                path={`/${tab.value}`}
                element={
                  <GrafanaDashboard 
                    dashboardId={dashboardUid} 
                    title={tab.label} 
                    pspCode={tab.systemOnly ? undefined : pspCode} 
                  />
                }
              />
            );
          })}
        </Routes>
      )}
    </Box>
  );
}

