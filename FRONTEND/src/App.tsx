import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CssBaseline } from "@mui/material";
import { AuthProvider, useAuth } from "./contexts/AuthContext";
import { isPlatformAdmin } from "./pages/EdgeNodes/edgeMeta";
import { ThemeProvider } from "./contexts/ThemeContext";
import ProtectedRoute from "./components/Auth/ProtectedRoute";
import HokekaLayout from "./layouts/HokekaLayout";
import { RouteErrorBoundary } from "./components/Common/RouteErrorBoundary";

import LoginPage from "./pages/Auth/LoginPage";
import SignupPage from "./pages/Auth/SignupPage";

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
const ReportsCenterPage = lazy(() => import("./pages/ReportsCenter/ReportsCenterPage"));
const AuditLogsPage = lazy(() => import("./pages/AuditLogs/AuditLogsPage"));
const RuntimeErrorsPage = lazy(() => import("./pages/RuntimeErrors/RuntimeErrorsPage"));
const RulesGenerationPage = lazy(() => import("./pages/RulesGeneration/RulesGenerationPage"));
const KycDocumentsPage = lazy(() => import("./pages/KycDocuments/KycDocumentsPage"));
const KycMerchantDetailPage = lazy(() => import("./pages/KycDocuments/KycMerchantDetailPage"));
const AnalyticsPage = lazy(() => import("./pages/Analytics/AnalyticsPage"));
const RegulatoryReportsPage = lazy(() => import("./pages/RegulatoryReports/RegulatoryReportsPage"));
const PspsListPage = lazy(() => import("./pages/Psps/PspsListPage"));
const PspConfigPage = lazy(() => import("./pages/Psps/PspConfigPage"));
const EdgeNodesPage = lazy(() => import("./pages/EdgeNodes/EdgeNodesPage"));
const LimitsAmlPage = lazy(() => import("./pages/LimitsAml/LimitsAmlPage"));
const BillingPage = lazy(() => import("./pages/Billing/BillingPage"));
const ChargebacksPage = lazy(() => import("./pages/Chargebacks/ChargebacksPage"));
const Customer360Page = lazy(() => import("./pages/Customer360/Customer360Page"));
const MarketSurveillancePage = lazy(() => import("./pages/MarketSurveillance/MarketSurveillancePage"));
const MobileMoneyPage = lazy(() => import("./pages/MobileMoney/MobileMoneyPage"));
const WalletIntelligencePage = lazy(() => import("./pages/WalletIntelligence/WalletIntelligencePage"));
const RecordDetailPage = lazy(() => import("./pages/RecordDetail/RecordDetailPage"));

/**
 * `/billing` is the PLATFORM revenue console (cross-tenant totals, admin-only endpoints). A PSP user
 * landing here previously got a wall of 403s and silently-empty widgets. Route them to their own
 * self-service billing instead, where the plan/usage/invoice surface is scoped to their tenant.
 */
function BillingRoute() {
  const { user } = useAuth();
  if (!isPlatformAdmin(user?.role?.name)) {
    // Settings renders Billing as the PSP user's only (and default) tab.
    return <Navigate to="/settings" replace />;
  }
  return <BillingPage />;
}

function PageLoader() {
  return (
    <div className="flex h-[60vh] items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-gold/30 border-t-gold" />
    </div>
  );
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
      retry: 1,
      staleTime: 45_000,
      gcTime: 10 * 60_000,
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
                    <HokekaLayout>
                      <RouteErrorBoundary>
                        <Suspense fallback={<PageLoader />}>
                          <Routes>
                            <Route index element={<Navigate to="/dashboard" replace />} />
                            <Route path="dashboard" element={<DashboardPage />} />
                            <Route path="cases/*" element={<CasesPage />} />
                            <Route path="alerts" element={<AlertsPage />} />
                            <Route path="risk-analytics" element={<RiskAnalyticsPage />} />
                            <Route path="compliance-calendar" element={<ComplianceCalendarPage />} />
                            <Route path="merchants" element={<MerchantsPage />} />
                            <Route path="transaction-monitoring/*" element={<TransactionMonitoringPage />} />
                            <Route path="customer-360" element={<Customer360Page />} />
                            <Route path="market-surveillance" element={<MarketSurveillancePage />} />
                            <Route path="mobile-money" element={<MobileMoneyPage />} />
                            <Route path="wallet-intelligence" element={<WalletIntelligencePage />} />
                            <Route path="records/:recordType/:recordId" element={<RecordDetailPage />} />
                            <Route path="screening" element={<ScreeningPage />} />
                            <Route path="profile" element={<ProfilePage />} />
                            <Route path="messages" element={<MessagesPage />} />
                            <Route path="settings" element={<SettingsPage />} />
                            <Route path="users/*" element={<UsersPage />} />
                            <Route path="reports" element={<ReportsCenterPage />} />
                            <Route path="chargebacks" element={<ChargebacksPage />} />
                            <Route path="reports-center" element={<Navigate to="/reports" replace />} />
                            <Route path="audit" element={<AuditLogsPage />} />
                            <Route path="runtime-errors" element={<RuntimeErrorsPage />} />
                            <Route path="rules-generation" element={<RulesGenerationPage />} />
                            <Route path="kyc-documents" element={<KycDocumentsPage />} />
                            <Route path="kyc-documents/:merchantId" element={<KycMerchantDetailPage />} />
                            <Route path="analytics" element={<AnalyticsPage />} />
                            <Route path="regulatory-reports" element={<RegulatoryReportsPage />} />
                            <Route path="psps" element={<PspsListPage />} />
                            <Route path="edge-nodes" element={<EdgeNodesPage />} />
                            <Route path="organization" element={<PspConfigPage />} />
                            <Route path="psps/:pspId/configure" element={<PspConfigPage />} />
                            <Route path="limits-aml" element={<LimitsAmlPage />} />
                            <Route path="billing" element={<BillingRoute />} />
                          </Routes>
                        </Suspense>
                      </RouteErrorBoundary>
                    </HokekaLayout>
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
