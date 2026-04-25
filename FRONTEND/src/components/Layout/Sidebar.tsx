import { useState } from "react";
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
  Shield as ShieldIcon,
  Code as RulesIcon,
  Menu as MenuIcon,
  ChevronLeft as ChevronLeftIcon,
  VerifiedUser as KycIcon,
} from "@mui/icons-material";
import { useAlerts, useCases } from "../../features/api/queries";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

const drawerWidth = 240;
const miniDrawerWidth = 64;

interface NavSection {
  title?: string;
  items: NavItem[];
}

interface NavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path: string;
  badge?: number;
  children?: NavItem[];
}

export default function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [openSections, setOpenSections] = useState<Record<string, boolean>>({
    dashboard: true,
  });

  // Fetch live badge counts — data cached with staleTime=30s so no extra load
  const { data: alertsData } = useAlerts({ page: 0, size: 1, status: "OPEN" });
  const { data: casesData } = useCases({ page: 0, size: 1, status: "NEW" });
  const { data: messagesData } = useQuery<{ unreadCount?: number }>({
    queryKey: ["messages", "unread-count"],
    queryFn: () => apiClient.get<{ unreadCount?: number }>("messages/unread-count").catch(() => ({ unreadCount: 0 })),
  });

  const openAlertsCount = alertsData?.totalElements || 0;
  const newCasesCount = casesData?.totalElements || 0;
  const unreadMessages = messagesData?.unreadCount || 0;

  const navSections: NavSection[] = [
    {
      items: [
        {
          id: "dashboard",
          label: "Dashboard",
          icon: <DashboardIcon />,
          path: "/dashboard",
        },
        {
          id: "cases",
          label: "Cases",
          icon: <CasesIcon />,
          path: "/cases",
          badge: newCasesCount,
        },
        {
          id: "alerts",
          label: "Alerts",
          icon: <AlertsIcon />,
          path: "/alerts",
          badge: openAlertsCount,
        },
        {
          id: "risk-analytics",
          label: "Risk Analytics",
          icon: <RiskIcon />,
          path: "/risk-analytics",
        },
        {
          id: "compliance-calendar",
          label: "Compliance Calendar",
          icon: <CalendarIcon />,
          path: "/compliance-calendar",
        },
        {
          id: "merchants",
          label: "Merchants",
          icon: <MerchantsIcon />,
          path: "/merchants",
        },
        {
          id: "transaction-monitoring",
          label: "Transaction Monitoring",
          icon: <MonitoringIcon />,
          path: "/transaction-monitoring",
        },
        {
          id: "screening",
          label: "Screening",
          icon: <ScreeningIcon />,
          path: "/screening",
        },
        {
          id: "kyc-documents",
          label: "KYC / Documents",
          icon: <KycIcon />,
          path: "/kyc-documents",
        },
        {
          id: "rules-generation",
          label: "Limits & AML Rules",
          icon: <RulesIcon />,
          path: "/rules-generation",
        },
        {
          id: "profile",
          label: "Profile",
          icon: <ProfileIcon />,
          path: "/profile",
        },
        {
          id: "messages",
          label: "Messages",
          icon: <MessagesIcon />,
          path: "/messages",
          badge: unreadMessages,
        },
      ],
    },
    {
      title: "ADMINISTRATION",
      items: [
        {
          id: "settings",
          label: "Settings",
          icon: <SettingsIcon />,
          path: "/settings",
        },
        {
          id: "users",
          label: "User Management",
          icon: <UsersIcon />,
          path: "/users",
        },
        {
          id: "reports",
          label: "Reports",
          icon: <ChartsIcon />,
          path: "/reports",
          children: [
            {
              id: "reports-summary",
              label: "Summary",
              icon: <ChartsIcon />,
              path: "/reports",
            },
            {
              id: "reports-center",
              label: "Reports Center",
              icon: <ChartsIcon />,
              path: "/reports-center",
            },
          ],
        },
        {
          id: "audit",
          label: "Audit Logs",
          icon: <AuditIcon />,
          path: "/audit",
        },
      ],
    },
  ];

  const toggleSection = (sectionId: string) => {
    setOpenSections((prev) => ({
      ...prev,
      [sectionId]: !prev[sectionId],
    }));
  };

  const isActive = (path: string) => {
    return location.pathname === path || location.pathname.startsWith(path + "/");
  };

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
          <>
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
                  src="/hokeka-logo.jpg"
                  alt="Hokeka Logo"
                  style={{
                    width: "100%",
                    height: "100%",
                    objectFit: "cover"
                  }}
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
          </>
        )}
        {collapsed && (
          <Box
            sx={{
              background: "linear-gradient(135deg, #a93226 0%, #d4ac0d 100%)",
              borderRadius: "10px",
              p: 0.6,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              mx: "auto"
            }}
          >
            <ShieldIcon sx={{ color: "#fff", fontSize: 20 }} />
          </Box>
        )}
        <Tooltip title={collapsed ? "Expand sidebar" : "Collapse sidebar"} arrow placement="right">
          <IconButton
            onClick={() => setCollapsed(!collapsed)}
            sx={{
              ml: collapsed ? 0 : 1,
              color: "text.secondary",
              "&:hover": { bgcolor: "rgba(0,0,0,0.05)" }
            }}
          >
            {collapsed ? <MenuIcon /> : <ChevronLeftIcon />}
          </IconButton>
        </Tooltip>
      </Box>

      <Box sx={{ overflow: "auto", flex: 1, px: 2 }}>
        {navSections.map((section, idx) => (
          <Box key={idx} sx={{ mb: 2 }}>
            {section.title && !collapsed && (
              <Typography
                variant="caption"
                sx={{
                  px: 2,
                  py: 0.5,
                  display: "block",
                  color: "text.secondary",
                  fontSize: "0.7rem",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.5px"
                }}
              >
                {section.title}
              </Typography>
            )}
            <List disablePadding>
              {section.items.map((item) => {
                const active = isActive(item.path);
                const hasChildren = item.children && item.children.length > 0;
                const isOpen = openSections[item.id] || false;

                return (
                  <Box key={item.id} sx={{ mb: 0.25 }}>
                    <ListItem disablePadding>
                      <Tooltip title={item.label} placement="right" arrow>
                        <ListItemButton
                          onClick={() => {
                            if (hasChildren) {
                              if (!collapsed) toggleSection(item.id);
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
                              color: "#a93226",
                              fontWeight: 600,
                              "&:hover": { backgroundColor: "rgba(169, 50, 38, 0.15)" },
                            },
                            "&:hover": {
                              backgroundColor: "rgba(0,0,0,0.03)",
                            },
                          }}
                        >
                          <ListItemIcon sx={{ minWidth: collapsed ? 0 : 32, color: active ? "#a93226" : "text.secondary" }}>
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
                                  sx: {
                                    fontSize: "0.85rem",
                                    fontWeight: active ? 600 : 500
                                  }
                                }}
                              />
                              {hasChildren && (isOpen ? <ExpandLess sx={{ opacity: 0.5 }} /> : <ExpandMore sx={{ opacity: 0.5 }} />)}
                            </>
                          )}
                        </ListItemButton>
                      </Tooltip>
                    </ListItem>
                    {hasChildren && !collapsed && (
                      <Collapse in={isOpen} timeout="auto" unmountOnExit>
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
                                  "&.Mui-selected": { color: "#6C5DD3", bgcolor: "transparent" }
                                }}
                                selected={isActive(child.path)}
                                onClick={() => navigate(child.path)}
                              >
                                <Box sx={{ width: 5, height: 5, borderRadius: "50%", bgcolor: isActive(child.path) ? "#6C5DD3" : "rgba(0,0,0,0.2)", mr: 1.5 }} />
                                <ListItemText
                                  primary={child.label}
                                  primaryTypographyProps={{ sx: { fontSize: "0.8rem" } }}
                                />
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
          </Box>
        ))}
      </Box>
    </Drawer>
  );
}
