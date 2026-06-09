import { useEffect, useMemo, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Collapse,
  Badge,
  Typography,
  IconButton,
  Tooltip,
} from "@mui/material";
import {
  Dashboard as DashboardIcon,
  FolderOpen as CasesIcon,
  Notifications as AlertsIcon,
  TrendingUp as RiskIcon,
  CalendarToday as CalendarIcon,
  Business as MerchantsIcon,
  Visibility as MonitoringIcon,
  Search as ScreeningIcon,
  Person as ProfileIcon,
  Mail as MessagesIcon,
  Settings as SettingsIcon,
  People as UsersIcon,
  Assessment as ChartsIcon,
  History as AuditIcon,
  ExpandLess,
  ExpandMore,
  Code as RulesIcon,
  Menu as MenuIcon,
  ChevronLeft as ChevronLeftIcon,
  VerifiedUser as KycIcon,
  AccountBalance as PspIcon,
  Description as RegulatoryIcon,
  Receipt as BillingIcon,
  Tune as LimitsIcon,
} from "@mui/icons-material";
import { useAlerts, useCases } from "../../features/api/queries";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { useAuth } from "../../contexts/AuthContext";

const drawerWidth = 240;
const miniDrawerWidth = 64;

interface NavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path: string;
  badge?: number;
  children?: NavItem[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

function isRouteActive(pathname: string, path: string): boolean {
  return pathname === path || pathname.startsWith(`${path}/`);
}

function findActiveSection(pathname: string, sections: NavSection[]): string {
  for (const section of sections) {
    const hasActive = section.items.some(
      (item) =>
        isRouteActive(pathname, item.path) ||
        item.children?.some((child) => isRouteActive(pathname, child.path)),
    );
    if (hasActive) return section.title;
  }
  return sections[0]?.title ?? "INTELLIGENCE";
}

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [expandedSection, setExpandedSection] = useState<string>("INTELLIGENCE");
  const [openSubMenus, setOpenSubMenus] = useState<Record<string, boolean>>({});

  const roleName = user?.role?.name ?? "";
  const isBillingAdmin = roleName === "SUPER_ADMIN" || roleName === "ADMIN";

  const { data: alertsData } = useAlerts({ page: 0, size: 1, status: "OPEN" });
  const { data: casesData } = useCases({ page: 0, size: 1, status: "NEW" });
  const { data: messagesData } = useQuery<{ count?: number }>({
    queryKey: ["messages", "unread-count"],
    queryFn: () => apiClient.get<{ count?: number }>("messages/unread/count").catch(() => ({ count: 0 })),
  });

  const openAlertsCount = alertsData?.totalElements || 0;
  const newCasesCount = casesData?.totalElements || 0;
  const unreadMessages = messagesData?.count || 0;

  const navSections: NavSection[] = useMemo(
    () => [
      {
        title: "INTELLIGENCE",
        items: [
          { id: "dashboard", label: "Dashboard", icon: <DashboardIcon />, path: "/dashboard" },
          {
            id: "transaction-monitoring",
            label: "Transaction Monitoring",
            icon: <MonitoringIcon />,
            path: "/transaction-monitoring",
          },
          { id: "alerts", label: "Alerts", icon: <AlertsIcon />, path: "/alerts", badge: openAlertsCount },
          { id: "cases", label: "Cases", icon: <CasesIcon />, path: "/cases", badge: newCasesCount },
        ],
      },
      {
        title: "COMPLIANCE",
        items: [
          { id: "kyc-documents", label: "KYC / Documents", icon: <KycIcon />, path: "/kyc-documents" },
          { id: "screening", label: "Screening", icon: <ScreeningIcon />, path: "/screening" },
          { id: "rules-generation", label: "Limits & AML Rules", icon: <RulesIcon />, path: "/rules-generation" },
          { id: "limits-aml", label: "Transaction Limits", icon: <LimitsIcon />, path: "/limits-aml" },
          {
            id: "compliance-calendar",
            label: "Compliance Calendar",
            icon: <CalendarIcon />,
            path: "/compliance-calendar",
          },
          {
            id: "regulatory-reports",
            label: "Regulatory Reports",
            icon: <RegulatoryIcon />,
            path: "/regulatory-reports",
          },
        ],
      },
      {
        title: "ANALYTICS",
        items: [
          { id: "risk-analytics", label: "Risk Analytics", icon: <RiskIcon />, path: "/risk-analytics" },
          { id: "analytics", label: "Analytics", icon: <ChartsIcon />, path: "/analytics" },
          {
            id: "reports",
            label: "Reports Center",
            icon: <ChartsIcon />,
            path: "/reports",
          },
          { id: "audit", label: "Audit Logs", icon: <AuditIcon />, path: "/audit" },
        ],
      },
      {
        title: "ADMINISTRATION",
        items: [
          { id: "merchants", label: "Merchants", icon: <MerchantsIcon />, path: "/merchants" },
          { id: "psps", label: "PSPs", icon: <PspIcon />, path: "/psps" },
          { id: "profile", label: "Profile", icon: <ProfileIcon />, path: "/profile" },
          {
            id: "messages",
            label: "Messages",
            icon: <MessagesIcon />,
            path: "/messages",
            badge: unreadMessages,
          },
          { id: "settings", label: "Settings", icon: <SettingsIcon />, path: "/settings" },
          { id: "users", label: "User Management", icon: <UsersIcon />, path: "/users" },
          ...(isBillingAdmin
            ? [{ id: "billing", label: "Billing", icon: <BillingIcon />, path: "/billing" } satisfies NavItem]
            : []),
        ],
      },
    ],
    [openAlertsCount, newCasesCount, unreadMessages, isBillingAdmin],
  );

  useEffect(() => {
    setExpandedSection(findActiveSection(location.pathname, navSections));
  }, [location.pathname, navSections]);

  const toggleSection = (title: string) => {
    setExpandedSection(title);
  };

  const toggleSubMenu = (itemId: string) => {
    setOpenSubMenus((prev) => ({ ...prev, [itemId]: !prev[itemId] }));
  };

  const isActive = (path: string) => isRouteActive(location.pathname, path);

  const sectionBadgeTotal = (section: NavSection) =>
    section.items.reduce((sum, item) => sum + (item.badge ?? 0), 0);

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: collapsed ? miniDrawerWidth : drawerWidth,
        flexShrink: 0,
        transition: "width 0.3s ease",
        "& .MuiDrawer-paper": {
          width: collapsed ? miniDrawerWidth : drawerWidth,
          boxSizing: "border-box",
          backgroundColor: "#FFFFFF",
          color: "text.primary",
          borderRight: "none",
          boxShadow: "4px 0 20px rgba(0,0,0,0.02)",
          m: 0.5,
          height: "calc(100vh - 8px)",
          borderRadius: "8px",
          transition: "width 0.3s ease",
          overflowX: "hidden",
        },
      }}
    >
      <Box sx={{ p: collapsed ? 1.5 : 2, display: "flex", alignItems: "center", justifyContent: "space-between", mb: 0.5 }}>
        {!collapsed && (
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <Box
              sx={{
                width: 36,
                height: 36,
                borderRadius: "10px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                overflow: "hidden",
              }}
            >
              <img
                src="/images/hokeka-logo.png"
                alt="Hokeka Logo"
                style={{ width: "100%", height: "100%", objectFit: "contain" }}
              />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700, fontSize: "0.95rem", lineHeight: 1.2 }}>
                AML Fraud Detector
              </Typography>
              <Typography variant="caption" sx={{ color: "text.secondary", fontSize: "0.65rem", display: "block" }}>
                Powered by Hokeka
              </Typography>
            </Box>
          </Box>
        )}
        {collapsed && (
          <Box sx={{ borderRadius: "10px", display: "flex", alignItems: "center", justifyContent: "center", mx: "auto", overflow: "hidden" }}>
            <img src="/images/hokeka-logo.png" alt="Hokeka Logo" style={{ width: 36, height: 36, objectFit: "contain" }} />
          </Box>
        )}
        <Tooltip title={collapsed ? "Expand sidebar" : "Collapse sidebar"} arrow placement="right">
          <IconButton
            onClick={() => setCollapsed(!collapsed)}
            sx={{ ml: collapsed ? 0 : 1, color: "text.secondary", "&:hover": { bgcolor: "rgba(0,0,0,0.05)" } }}
          >
            {collapsed ? <MenuIcon /> : <ChevronLeftIcon />}
          </IconButton>
        </Tooltip>
      </Box>

      <Box sx={{ overflow: "auto", flex: 1, px: 2 }}>
        {navSections.map((section) => {
          const isExpanded = expandedSection === section.title;
          const badgeTotal = sectionBadgeTotal(section);

          return (
            <Box key={section.title} sx={{ mb: 1 }}>
              {!collapsed && (
                <ListItemButton
                  onClick={() => toggleSection(section.title)}
                  sx={{
                    borderRadius: "8px",
                    py: 0.5,
                    px: 1.5,
                    minHeight: 32,
                    mb: 0.25,
                    "&:hover": { bgcolor: "rgba(0,0,0,0.03)" },
                  }}
                >
                  {isExpanded ? (
                    <ExpandLess sx={{ fontSize: 16, opacity: 0.5, mr: 0.5 }} />
                  ) : (
                    <ExpandMore sx={{ fontSize: 16, opacity: 0.5, mr: 0.5 }} />
                  )}
                  <Typography
                    variant="caption"
                    sx={{
                      flex: 1,
                      color: "text.secondary",
                      fontSize: "0.7rem",
                      fontWeight: 700,
                      textTransform: "uppercase",
                      letterSpacing: "0.5px",
                    }}
                  >
                    {section.title}
                  </Typography>
                  {!isExpanded && (
                    <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                      {badgeTotal > 0 && (
                        <Badge badgeContent={badgeTotal > 99 ? "99+" : badgeTotal} color="error" max={999} />
                      )}
                      <Typography variant="caption" sx={{ color: "text.disabled", fontSize: "0.65rem" }}>
                        {section.items.length}
                      </Typography>
                    </Box>
                  )}
                </ListItemButton>
              )}

              <Collapse in={collapsed || isExpanded} timeout="auto" unmountOnExit={!collapsed}>
                <List disablePadding>
                  {section.items.map((item) => {
                    const active = isActive(item.path);
                    const hasChildren = item.children && item.children.length > 0;
                    const isSubOpen = openSubMenus[item.id] || false;

                    return (
                      <Box key={item.id} sx={{ mb: 0.25 }}>
                        <ListItem disablePadding>
                          <Tooltip title={item.label} placement="right" arrow>
                            <ListItemButton
                              onClick={() => {
                                if (hasChildren) {
                                  if (!collapsed) toggleSubMenu(item.id);
                                } else {
                                  navigate(item.path);
                                }
                              }}
                              selected={active && !hasChildren}
                              sx={{
                                borderRadius: "10px",
                                mb: 0.25,
                                py: 0.8,
                                px: collapsed ? 1.5 : 2,
                                justifyContent: collapsed ? "center" : "flex-start",
                                minHeight: 40,
                                "&.Mui-selected": {
                                  backgroundColor: "rgba(169, 50, 38, 0.1)",
                                  color: "primary.main",
                                  fontWeight: 600,
                                  "&:hover": { backgroundColor: "rgba(169, 50, 38, 0.15)" },
                                },
                                "&:hover": { backgroundColor: "rgba(0,0,0,0.03)" },
                              }}
                            >
                              <ListItemIcon sx={{ minWidth: collapsed ? 0 : 32, color: active ? "primary.main" : "text.secondary" }}>
                                {item.badge !== undefined && item.badge > 0 ? (
                                  <Badge badgeContent={item.badge > 99 ? "99+" : item.badge} color="error" max={999}>
                                    {item.icon}
                                  </Badge>
                                ) : (
                                  item.icon
                                )}
                              </ListItemIcon>
                              {!collapsed && (
                                <>
                                  <ListItemText
                                    primary={item.label}
                                    primaryTypographyProps={{
                                      sx: { fontSize: "0.85rem", fontWeight: active ? 600 : 500 },
                                    }}
                                  />
                                  {hasChildren && (isSubOpen ? <ExpandLess sx={{ opacity: 0.5 }} /> : <ExpandMore sx={{ opacity: 0.5 }} />)}
                                </>
                              )}
                            </ListItemButton>
                          </Tooltip>
                        </ListItem>
                        {hasChildren && !collapsed && (
                          <Collapse in={isSubOpen} timeout="auto" unmountOnExit>
                            <List component="div" disablePadding>
                              {item.children!.map((child) => (
                                <Tooltip key={child.id} title={child.label} placement="right" arrow>
                                  <ListItemButton
                                    sx={{
                                      pl: 5,
                                      borderRadius: "8px",
                                      mb: 0.1,
                                      py: 0.6,
                                      minHeight: 32,
                                      "&.Mui-selected": { color: "#6C5DD3", bgcolor: "transparent" },
                                    }}
                                    selected={isActive(child.path)}
                                    onClick={() => navigate(child.path)}
                                  >
                                    <Box
                                      sx={{
                                        width: 5,
                                        height: 5,
                                        borderRadius: "50%",
                                        bgcolor: isActive(child.path) ? "#6C5DD3" : "rgba(0,0,0,0.2)",
                                        mr: 1.5,
                                      }}
                                    />
                                    <ListItemText primary={child.label} primaryTypographyProps={{ sx: { fontSize: "0.8rem" } }} />
                                  </ListItemButton>
                                </Tooltip>
                              ))}
                            </List>
                          </Collapse>
                        )}
                      </Box>
                    );
                  })}
                </List>
              </Collapse>
            </Box>
          );
        })}
      </Box>
    </Drawer>
  );
}
