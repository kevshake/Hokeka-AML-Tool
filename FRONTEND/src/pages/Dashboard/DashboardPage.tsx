import { Box, Typography, Grid, Card, CardContent, Chip, Button, CircularProgress, Paper, Avatar } from "@mui/material";
import { useNavigate } from "react-router-dom";
import {
  useDashboardStats,
  useCases,
  useAlerts,
  useRecentTransactions,
  useLiveAlerts,
  useMonitoringTransactions,
} from "../../features/api/queries";
import {
  Warning as WarningIcon,
  Assignment as AssignmentIcon,
  TrendingUp as TrendingUpIcon,
  AccountBalance as AccountBalanceIcon,
  ArrowForward as ArrowForwardIcon,
  Notifications as NotificationsIcon,
} from "@mui/icons-material";

// Stat Card Component
interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  color: string;
  onClick?: () => void;
}

function StatCard({ title, value, subtitle, icon, color, onClick }: StatCardProps) {
  return (
    <Card 
      sx={{ 
        cursor: onClick ? "pointer" : "default",
        transition: "all 0.2s ease",
        "&:hover": onClick ? {
          transform: "translateY(-2px)",
          boxShadow: "0 8px 24px rgba(0,0,0,0.12)",
        } : {},
      }}
      onClick={onClick}
    >
      <CardContent sx={{ p: 3 }}>
        <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
          <Box>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 1, fontWeight: 500 }}>
              {title}
            </Typography>
            <Typography variant="h4" sx={{ color: "text.primary", fontWeight: 700, mb: 0.5 }}>
              {value}
            </Typography>
            {subtitle && (
              <Typography variant="caption" sx={{ color: "text.secondary" }}>
                {subtitle}
              </Typography>
            )}
          </Box>
          <Avatar sx={{ bgcolor: `${color}20`, color: color, width: 48, height: 48 }}>
            {icon}
          </Avatar>
        </Box>
      </CardContent>
    </Card>
  );
}

// Recent Activity Item
interface ActivityItemProps {
  title: string;
  time: string;
  type: "alert" | "case" | "transaction";
}

function ActivityItem({ title, time, type }: ActivityItemProps) {
  const colors = {
    alert: "#e74c3c",
    case: "#8B4049",
    transaction: "#27ae60",
  };

  return (
    <Box sx={{ display: "flex", alignItems: "center", py: 1.5, borderBottom: "1px solid rgba(0,0,0,0.06)" }}>
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: "50%",
          backgroundColor: colors[type],
          mr: 2,
        }}
      />
      <Box sx={{ flexGrow: 1 }}>
        <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 500 }}>
          {title}
        </Typography>
        <Typography variant="caption" sx={{ color: "text.secondary" }}>
          {time}
        </Typography>
      </Box>
    </Box>
  );
}

export default function DashboardPage() {
  const navigate = useNavigate();
  
  const { data: stats, isLoading: statsLoading } = useDashboardStats();
  const { data: casesData } = useCases({ page: 0, size: 5 });
  const { data: alertsData } = useAlerts({ page: 0, size: 5 });
  const { data: recentTransactions } = useRecentTransactions(5);
  const { data: liveAlerts } = useLiveAlerts(5);
  const { data: txnPage } = useMonitoringTransactions({ page: 0, size: 1 });

  // Calculate stats
  const totalCases = Object.values(stats?.casesByStatus || {}).reduce((a: number, b: number) => a + b, 0) || 0;
  const openCases = (stats?.casesByStatus?.NEW || 0) + (stats?.casesByStatus?.ASSIGNED || 0) + (stats?.casesByStatus?.INVESTIGATING || 0);
  const pendingAlerts = alertsData?.totalElements || 0;
  const recentAuditCount = stats?.auditLast24h || 0;

  if (statsLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "60vh" }}>
        <CircularProgress size={60} sx={{ color: "#8B4049" }} />
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" sx={{ color: "text.primary", mb: 1, fontWeight: 700 }}>
        Dashboard Overview
      </Typography>
      <Typography variant="body2" sx={{ color: "text.secondary", mb: 3 }}>
        Welcome back! Here's what's happening in your compliance system.
      </Typography>

      {/* Stats Grid */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Open Cases"
            value={openCases}
            subtitle={`${totalCases} total cases`}
            icon={<AssignmentIcon />}
            color="#8B4049"
            onClick={() => navigate("/cases")}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Pending Alerts"
            value={pendingAlerts}
            subtitle="Requires attention"
            icon={<WarningIcon />}
            color="#e74c3c"
            onClick={() => navigate("/alerts")}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Transactions"
            value={txnPage?.totalElements?.toLocaleString() ?? (recentTransactions?.length || 0)}
            subtitle="Total monitored"
            icon={<TrendingUpIcon />}
            color="#27ae60"
            onClick={() => navigate("/transaction-monitoring")}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Audit Events"
            value={recentAuditCount}
            subtitle="Last 24 hours"
            icon={<AccountBalanceIcon />}
            color="#3498db"
            onClick={() => navigate("/audit")}
          />
        </Grid>
      </Grid>

      {/* Main Content Grid */}
      <Grid container spacing={3}>
        {/* Recent Alerts */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)" }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <NotificationsIcon sx={{ color: "#e74c3c" }} />
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Live Alerts
                </Typography>
              </Box>
              <Button 
                size="small" 
                endIcon={<ArrowForwardIcon />}
                onClick={() => navigate("/alerts")}
                sx={{ color: "#8B4049" }}
              >
                View All
              </Button>
            </Box>
            {liveAlerts && liveAlerts.length > 0 ? (
              liveAlerts.map((alert: any) => (
                <ActivityItem
                  key={alert.id}
                  title={alert.description || `Alert #${alert.id}`}
                  time={new Date(alert.createdAt).toLocaleString()}
                  type="alert"
                />
              ))
            ) : (
              <Typography variant="body2" sx={{ color: "text.secondary", py: 2 }}>
                No active alerts
              </Typography>
            )}
          </Paper>
        </Grid>

        {/* Recent Cases */}
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)" }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <AssignmentIcon sx={{ color: "#8B4049" }} />
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Recent Cases
                </Typography>
              </Box>
              <Button 
                size="small" 
                endIcon={<ArrowForwardIcon />}
                onClick={() => navigate("/cases")}
                sx={{ color: "#8B4049" }}
              >
                View All
              </Button>
            </Box>
            {casesData?.content && casesData.content.length > 0 ? (
              casesData.content.slice(0, 5).map((caseItem: any) => (
                <Box 
                  key={caseItem.id} 
                  sx={{ 
                    display: "flex", 
                    alignItems: "center", 
                    py: 1.5, 
                    borderBottom: "1px solid rgba(0,0,0,0.06)",
                    cursor: "pointer",
                    "&:hover": { backgroundColor: "rgba(0,0,0,0.02)" },
                  }}
                  onClick={() => navigate(`/cases/${caseItem.id}`)}
                >
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {caseItem.caseReference}
                    </Typography>
                    <Typography variant="caption" sx={{ color: "text.secondary" }}>
                      {caseItem.description ? `${caseItem.description.substring(0, 50)}...` : ""}
                    </Typography>
                  </Box>
                  <Chip 
                    label={caseItem.status} 
                    size="small"
                    sx={{
                      backgroundColor: caseItem.status === "NEW" ? "#ebf5fb" : 
                                      caseItem.status === "RESOLVED" ? "#e9f7ef" : "#fef9e7",
                      color: caseItem.status === "NEW" ? "#3498db" : 
                             caseItem.status === "RESOLVED" ? "#27ae60" : "#8e6b3e",
                      fontWeight: 600,
                    }}
                  />
                </Box>
              ))
            ) : (
              <Typography variant="body2" sx={{ color: "text.secondary", py: 2 }}>
                No recent cases
              </Typography>
            )}
          </Paper>
        </Grid>

        {/* Recent Transactions */}
        <Grid item xs={12}>
          <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)" }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <TrendingUpIcon sx={{ color: "#27ae60" }} />
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Recent Transactions
                </Typography>
              </Box>
              <Button 
                size="small" 
                endIcon={<ArrowForwardIcon />}
                onClick={() => navigate("/transaction-monitoring")}
                sx={{ color: "#8B4049" }}
              >
                View All
              </Button>
            </Box>
            {recentTransactions && recentTransactions.length > 0 ? (
              <Box sx={{ display: "flex", gap: 2, overflowX: "auto", pb: 1 }}>
  {recentTransactions.map((txn: any) => (
                   <Card key={txn.txnId || txn.id} sx={{ minWidth: 200, p: 2 }}>
                    <Typography variant="caption" sx={{ color: "text.secondary" }}>
                      Transaction #{txn.txnId || txn.id || "N/A"}
                    </Typography>
                    <Typography variant="h6" sx={{ fontWeight: 600, my: 1 }}>
                      {txn.amountCents ? `$${(txn.amountCents / 100).toFixed(2)}` : "-"}
                    </Typography>
                    <Chip
                      label={txn.decision || "ALLOW"}
                      size="small"
                      sx={{
                        backgroundColor: txn.decision === "BLOCK" ? "#e74c3c20" : "#2ecc7120",
                        color: txn.decision === "BLOCK" ? "#e74c3c" : "#2ecc71",
                        fontWeight: 600,
                      }}
                    />
                  </Card>
                ))}
              </Box>
            ) : (
              <Typography variant="body2" sx={{ color: "text.secondary", py: 2 }}>
                No recent transactions
              </Typography>
            )}
          </Paper>
        </Grid>

        {/* Quick Actions */}
        <Grid item xs={12}>
          <Paper sx={{ p: 3, borderRadius: 2, border: "1px solid rgba(0,0,0,0.08)", backgroundColor: "#fafafa" }}>
            <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
              Quick Actions
            </Typography>
            
            {/* Create Actions */}
            <Box sx={{ mb: 3 }}>
              <Typography variant="subtitle2" sx={{ color: "text.secondary", mb: 2, fontWeight: 600 }}>
                CREATE NEW
              </Typography>
              <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                <Button
                  variant="contained"
                  startIcon={<AssignmentIcon />}
                  onClick={() => navigate("/cases/all")}
                  sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                >
                  Create Case
                </Button>
                <Button
                  variant="contained"
                  startIcon={<TrendingUpIcon />}
                  onClick={() => navigate("/transaction-monitoring/live")}
                  sx={{ backgroundColor: "#27ae60", "&:hover": { backgroundColor: "#219a52" } }}
                >
                  Monitor Transactions
                </Button>
                <Button
                  variant="contained"
                  startIcon={<AccountBalanceIcon />}
                  onClick={() => navigate("/merchants")}
                  sx={{ backgroundColor: "#3498db", "&:hover": { backgroundColor: "#2980b9" } }}
                >
                  Add Merchant
                </Button>
                <Button
                  variant="contained"
                  startIcon={<NotificationsIcon />}
                  onClick={() => navigate("/alerts")}
                  sx={{ backgroundColor: "#e74c3c", "&:hover": { backgroundColor: "#c0392b" } }}
                >
                  View Alerts
                </Button>
              </Box>
            </Box>

            {/* View Pages */}
            <Box>
              <Typography variant="subtitle2" sx={{ color: "text.secondary", mb: 2, fontWeight: 600 }}>
                VIEW PAGES
              </Typography>
              <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                <Button
                  variant="outlined"
                  endIcon={<ArrowForwardIcon />}
                  onClick={() => navigate("/cases")}
                  sx={{ borderColor: "#8B4049", color: "#8B4049" }}
                >
                  View Cases
                </Button>
                <Button
                  variant="outlined"
                  endIcon={<ArrowForwardIcon />}
                  onClick={() => navigate("/transaction-monitoring")}
                  sx={{ borderColor: "#27ae60", color: "#27ae60" }}
                >
                  View Transactions
                </Button>
                <Button
                  variant="outlined"
                  endIcon={<ArrowForwardIcon />}
                  onClick={() => navigate("/merchants")}
                  sx={{ borderColor: "#3498db", color: "#3498db" }}
                >
                  View Merchants
                </Button>
                <Button
                  variant="outlined"
                  endIcon={<ArrowForwardIcon />}
                  onClick={() => navigate("/alerts")}
                  sx={{ borderColor: "#e74c3c", color: "#e74c3c" }}
                >
                  View Alerts
                </Button>
                <Button
                  variant="outlined"
                  endIcon={<ArrowForwardIcon />}
                  onClick={() => navigate("/reports")}
                  sx={{ borderColor: "#9b59b6", color: "#9b59b6" }}
                >
                  View Reports
                </Button>
              </Box>
            </Box>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
