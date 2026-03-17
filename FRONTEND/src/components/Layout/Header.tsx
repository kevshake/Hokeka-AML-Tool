import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  IconButton,
  InputBase,
  Box,
  Avatar,
  Menu,
  MenuItem,
  Typography,
  Chip,
  Tooltip,
  Breadcrumbs,
  Link,
} from "@mui/material";
import {
  Search as SearchIcon,
  Notifications as NotificationsIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  Logout as LogoutIcon,
  Home as HomeIcon,
} from "@mui/icons-material";
import { useAuth } from "../../contexts/AuthContext";

// Page title mapping
const pageTitles: Record<string, string> = {
  "/dashboard": "Dashboard",
  "/cases": "Cases",
  "/alerts": "Alerts",
  "/risk-analytics": "Risk Analytics",
  "/compliance-calendar": "Compliance Calendar",
  "/merchants": "Merchants",
  "/transaction-monitoring": "Transaction Monitoring",
  "/screening": "Screening",
  "/profile": "Profile",
  "/messages": "Messages",
  "/settings": "Settings",
  "/users": "User Management",
  "/reports": "Reports",
  "/reports-center": "Reports Center",
  "/audit": "Audit Logs",
  "/rules-generation": "Rules Generation",
};

export default function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const { user, logout } = useAuth();

  const handleProfileClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    handleClose();
    logout();
  };

  // Get current page title
  const currentPath = location.pathname;
  const pageTitle = pageTitles[currentPath] || "Page";
  
  // Generate breadcrumbs
  const pathSegments = currentPath.split("/").filter(Boolean);
  const breadcrumbs = pathSegments.map((segment, index) => {
    const path = "/" + pathSegments.slice(0, index + 1).join("/");
    const title = pageTitles[path] || segment.charAt(0).toUpperCase() + segment.slice(1);
    return { path, title, isLast: index === pathSegments.length - 1 };
  });

  const userName = user?.username || user?.email || "User";
  const userInitials = userName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2) || "U";

  return (
    <AppBar
      position="fixed"
      sx={{
        width: `calc(100% - 240px)`,
        ml: "240px",
        backgroundColor: "rgba(255,255,255,0.95)",
        backdropFilter: "blur(12px)",
        borderBottom: "1px solid rgba(0,0,0,0.08)",
        boxShadow: "0 2px 8px rgba(0,0,0,0.04)",
        color: "text.primary",
        height: "64px",
      }}
    >
      <Toolbar sx={{ minHeight: "64px", px: 3 }}>
        {/* Left Section - Breadcrumbs & Title */}
        <Box sx={{ display: "flex", flexDirection: "column", flexGrow: 1 }}>
          {/* Breadcrumbs */}
          <Breadcrumbs 
            separator="›" 
            sx={{ 
              mb: 0.5,
              "& .MuiBreadcrumbs-separator": { 
                color: "text.disabled",
                fontSize: "0.75rem",
              }
            }}
          >
            <Link
              underline="hover"
              color="inherit"
              onClick={() => navigate("/dashboard")}
              sx={{ 
                display: "flex", 
                alignItems: "center",
                cursor: "pointer",
                fontSize: "0.75rem",
                color: "text.secondary",
                "&:hover": { color: "primary.main" },
              }}
            >
              <HomeIcon sx={{ fontSize: 14, mr: 0.5 }} />
              Home
            </Link>
            {breadcrumbs.map((crumb) => (
              crumb.isLast ? (
                <Typography 
                  key={crumb.path}
                  sx={{ 
                    fontSize: "0.75rem",
                    color: "text.primary",
                    fontWeight: 500,
                  }}
                >
                  {crumb.title}
                </Typography>
              ) : (
                <Link
                  key={crumb.path}
                  underline="hover"
                  color="inherit"
                  onClick={() => navigate(crumb.path)}
                  sx={{ 
                    cursor: "pointer",
                    fontSize: "0.75rem",
                    color: "text.secondary",
                    "&:hover": { color: "primary.main" },
                  }}
                >
                  {crumb.title}
                </Link>
              )
            ))}
          </Breadcrumbs>
          
          {/* Page Title */}
          <Typography 
            variant="h6" 
            sx={{ 
              fontWeight: 700, 
              color: "#3D2C2E",
              fontSize: "1.1rem",
              lineHeight: 1.2,
            }}
          >
            {pageTitle}
          </Typography>
        </Box>

        {/* Right Section - Search, Notifications, Profile */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          {/* Search Bar */}
          <Box
            sx={{
              position: "relative",
              borderRadius: "10px",
              backgroundColor: "rgba(0, 0, 0, 0.04)",
              "&:hover": {
                backgroundColor: "rgba(0, 0, 0, 0.06)",
              },
              width: 280,
              height: 40,
              display: { xs: "none", md: "flex" },
              alignItems: "center",
            }}
          >
            <SearchIcon sx={{ color: "text.secondary", ml: 1.5, fontSize: 18 }} />
            <Tooltip title="Search across dashboard, transactions, cases, and reports" arrow>
              <InputBase
                placeholder="Search..."
                sx={{
                  color: "text.primary",
                  ml: 1,
                  flex: 1,
                  fontSize: "0.875rem",
                  "& .MuiInputBase-input": {
                    py: 0.5,
                  },
                }}
              />
            </Tooltip>
          </Box>

          {/* Session Status */}
          <Tooltip title="Your session is currently active and secure" arrow>
            <Chip
              label="Active"
              size="small"
              sx={{ 
                mr: 1, 
                borderRadius: "6px", 
                fontWeight: 600,
                fontSize: "0.7rem",
                height: 24,
                backgroundColor: "rgba(46, 204, 113, 0.15)",
                color: "#27ae60",
                border: "1px solid rgba(46, 204, 113, 0.3)",
              }}
            />
          </Tooltip>

          {/* Notifications */}
          <Tooltip title="View notifications and alerts" arrow>
            <IconButton 
              sx={{ 
                color: "text.secondary",
                width: 40,
                height: 40,
                borderRadius: "10px",
                "&:hover": {
                  backgroundColor: "rgba(0,0,0,0.04)",
                },
              }}
            >
              <NotificationsIcon sx={{ fontSize: 20 }} />
            </IconButton>
          </Tooltip>

          {/* User Profile */}
          <Tooltip title="Click to access your profile and account settings" arrow>
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                cursor: "pointer",
                px: 1.5,
                py: 0.75,
                borderRadius: "10px",
                border: "1px solid rgba(0,0,0,0.08)",
                backgroundColor: "rgba(255,255,255,0.5)",
                "&:hover": {
                  backgroundColor: "rgba(0,0,0,0.03)",
                  borderColor: "rgba(0,0,0,0.12)",
                },
              }}
              onClick={handleProfileClick}
            >
              <Avatar
                sx={{
                  width: 32,
                  height: 32,
                  bgcolor: "#8B4049",
                  fontSize: "0.8rem",
                  fontWeight: 600,
                }}
              >
                {userInitials}
              </Avatar>
              <Box sx={{ display: { xs: "none", sm: "block" } }}>
                <Typography variant="body2" sx={{ color: "text.primary", fontWeight: 600, lineHeight: 1.2 }}>
                  {userName}
                </Typography>
                <Typography variant="caption" sx={{ color: "text.secondary", fontSize: "0.7rem", display: "block" }}>
                  Admin
                </Typography>
              </Box>
            </Box>
          </Tooltip>

          {/* Profile Menu */}
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleClose}
            anchorOrigin={{
              vertical: "bottom",
              horizontal: "right",
            }}
            transformOrigin={{
              vertical: "top",
              horizontal: "right",
            }}
            PaperProps={{
              sx: {
                backgroundColor: "background.paper",
                color: "text.primary",
                mt: 1.5,
                borderRadius: "12px",
                boxShadow: "0px 10px 40px rgba(0,0,0,0.1)",
                minWidth: 180,
              },
            }}
          >
            <Tooltip title="View and edit your personal profile information" arrow>
              <MenuItem onClick={() => { navigate("/profile"); handleClose(); }} sx={{ py: 1 }}>
                <PersonIcon sx={{ mr: 2, color: "text.secondary", fontSize: 18 }} />
                <Typography variant="body2">My Profile</Typography>
              </MenuItem>
            </Tooltip>
            <Tooltip title="Configure application settings and preferences" arrow>
              <MenuItem onClick={() => { navigate("/settings"); handleClose(); }} sx={{ py: 1 }}>
                <SettingsIcon sx={{ mr: 2, color: "text.secondary", fontSize: 18 }} />
                <Typography variant="body2">Settings</Typography>
              </MenuItem>
            </Tooltip>
            <Tooltip title="Sign out of your account" arrow>
              <MenuItem onClick={handleLogout} sx={{ py: 1 }}>
                <LogoutIcon sx={{ mr: 2, color: "text.secondary", fontSize: 18 }} />
                <Typography variant="body2">Logout</Typography>
              </MenuItem>
            </Tooltip>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
}
