import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CssBaseline, CircularProgress, Box } from "@mui/material";
import { AuthProvider } from "./contexts/AuthContext";
import { ThemeProvider } from "./contexts/ThemeContext";
import ProtectedRoute from "./components/Auth/ProtectedRoute";
import MainLayout from "./components/Layout/MainLayout";
import { RouteErrorBoundary } from "./components/Common/RouteErrorBoundary";

// Auth pages — small, load eagerly so login is instant
import LoginPage from "./pages/Auth/LoginPage";
import SignupPage from "./pages/Auth/SignupPage";

// All other pages lazy-loaded so each route is its own chunk
const DashboardPage = lazy(() => import("./pages/Dashboard/DashboardPage"));
const CasesPage = lazy(() => import("./pages/Cases/CasesPage"));
const AlertsPage = lazy(() => import("./pages/Alerts/AlertsPage"));
const RiskAnalyticsPage = lazy(() => import("./pages/RiskAnalytics/RiskAnalyticsPage"));
const ComplianceCalendarPage = lazy(() => import("./pages/ComplianceCalendar/ComplianceCalendarPage"));
const MerchantsPage = lazy(() => import("./pages/Merchants/MerchantsPage"));
const TransactionMonitoringPage = lazy(() => import("./pages/TransactionMonitoring/TransactionMonitoringPage"));
const ScreeningPage = lazy(() => import("./pages/Screening/ScreeningPage"));
const ProfilePage = lazy(() => import("./pages/Profile/ProfilePage"));
const MessagesPage = lazy(() => import("./pages/Messages/MessagesPage"));
const SettingsPage = lazy(() => import("./pages/Settings/SettingsPage"));
const UsersPage = lazy(() => import("./pages/Users/UsersPage"));
const ReportsPage = lazy(() => import("./pages/Reports/ReportsPage"));
const ReportsCenterPage = lazy(() => import("./pages/ReportsCenter/ReportsCenterPage"));
const AuditLogsPage = lazy(() => import("./pages/AuditLogs/AuditLogsPage"));
const RulesGenerationPage = lazy(() => import("./pages/RulesGeneration/RulesGenerationPage"));
const KycDocumentsPage = lazy(() => import("./pages/KycDocuments/KycDocumentsPage"));
const AnalyticsPage = lazy(() => import("./pages/Analytics/AnalyticsPage"));

function PageLoader() {
  return (
    <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "60vh" }}>
      <CircularProgress size={32} sx={{ color: "#8B4049" }} />
    </Box>
  );
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,      // data is fresh for 30s — avoids refetch on every mount
      gcTime: 5 * 60_000,     // keep unused data in cache for 5 min
    },
  },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ThemeProvider>
            <CssBaseline />
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
              <Route
                path="/*"
                element={
                  <ProtectedRoute>
                    <MainLayout>
                      <RouteErrorBoundary>
                        <Suspense fallback={<PageLoader />}>
                        <Routes>
                          <Route index element={<Navigate to="/dashboard" replace />} />
                          <Route path="dashboard/*" element={<DashboardPage />} />
                          <Route path="cases/*" element={<CasesPage />} />
                          <Route path="alerts" element={<AlertsPage />} />
                          <Route path="risk-analytics" element={<RiskAnalyticsPage />} />
                          <Route path="compliance-calendar" element={<ComplianceCalendarPage />} />
                          <Route path="merchants" element={<MerchantsPage />} />
                          <Route path="transaction-monitoring/*" element={<TransactionMonitoringPage />} />
                          <Route path="screening" element={<ScreeningPage />} />
                          <Route path="profile" element={<ProfilePage />} />
                          <Route path="messages" element={<MessagesPage />} />
                          <Route path="settings" element={<SettingsPage />} />
                          <Route path="users/*" element={<UsersPage />} />
                          <Route path="reports" element={<ReportsPage />} />
                          <Route path="reports-center" element={<ReportsCenterPage />} />
                          <Route path="audit" element={<AuditLogsPage />} />
                          <Route path="rules-generation" element={<RulesGenerationPage />} />
                          <Route path="kyc-documents" element={<KycDocumentsPage />} />
                          <Route path="analytics" element={<AnalyticsPage />} />
                        </Routes>
                        </Suspense>
                      </RouteErrorBoundary>
                    </MainLayout>
                  </ProtectedRoute>
                }
              />
            </Routes>
          </ThemeProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
