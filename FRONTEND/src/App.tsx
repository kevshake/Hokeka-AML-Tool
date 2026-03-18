import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CssBaseline } from "@mui/material";
import { AuthProvider } from "./contexts/AuthContext";
import { ThemeProvider } from "./contexts/ThemeContext";
import ProtectedRoute from "./components/Auth/ProtectedRoute";
import MainLayout from "./components/Layout/MainLayout";

// Pages
import LoginPage from "./pages/Auth/LoginPage";
import SignupPage from "./pages/Auth/SignupPage";
import DashboardPage from "./pages/Dashboard/DashboardPage";
import CasesPage from "./pages/Cases/CasesPage";
import AlertsPage from "./pages/Alerts/AlertsPage";
import RiskAnalyticsPage from "./pages/RiskAnalytics/RiskAnalyticsPage";
import ComplianceCalendarPage from "./pages/ComplianceCalendar/ComplianceCalendarPage";
import MerchantsPage from "./pages/Merchants/MerchantsPage";
import TransactionMonitoringPage from "./pages/TransactionMonitoring/TransactionMonitoringPage";
import ScreeningPage from "./pages/Screening/ScreeningPage";
import ProfilePage from "./pages/Profile/ProfilePage";
import MessagesPage from "./pages/Messages/MessagesPage";
import SettingsPage from "./pages/Settings/SettingsPage";
import UsersPage from "./pages/Users/UsersPage";
import ReportsPage from "./pages/Reports/ReportsPage";
import ReportsCenterPage from "./pages/ReportsCenter/ReportsCenterPage";
import AuditLogsPage from "./pages/AuditLogs/AuditLogsPage";
import RulesGenerationPage from "./pages/RulesGeneration/RulesGenerationPage";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
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
                      <Routes>
                        <Route path="/" element={<Navigate to="/dashboard" replace />} />
                        <Route path="/dashboard/*" element={<DashboardPage />} />
                        <Route path="/cases/*" element={<CasesPage />} />
                        <Route path="/alerts" element={<AlertsPage />} />
                        <Route path="/risk-analytics" element={<RiskAnalyticsPage />} />
                        <Route path="/compliance-calendar" element={<ComplianceCalendarPage />} />
                        <Route path="/merchants" element={<MerchantsPage />} />
                        <Route path="/transaction-monitoring/*" element={<TransactionMonitoringPage />} />
                        <Route path="/screening" element={<ScreeningPage />} />
                        <Route path="/profile" element={<ProfilePage />} />
                        <Route path="/messages" element={<MessagesPage />} />
                        <Route path="/settings" element={<SettingsPage />} />
                        <Route path="/users/*" element={<UsersPage />} />
                        <Route path="/reports" element={<ReportsPage />} />
                        <Route path="/reports-center" element={<ReportsCenterPage />} />
                        <Route path="/audit" element={<AuditLogsPage />} />
                        <Route path="/rules-generation" element={<RulesGenerationPage />} />
                      </Routes>
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
