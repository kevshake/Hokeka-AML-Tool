import { useState } from "react";
import { useNavigate } from "react-router-dom";
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
} from "@mui/material";
import {
  Search as SearchIcon,
  Notifications as NotificationsIcon,
  Person as PersonIcon,
  Settings as SettingsIcon,
  Logout as LogoutIcon,
} from "@mui/icons-material";
import { useAuth } from "../../contexts/AuthContext";

export default function Header() {
  const navigate = useNavigate();
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
        width: `calc(100% - 248px)`,
        ml: "248px",
        backgroundColor: "rgba(255,255,255,0.8)",
        backdropFilter: "blur(12px)",
        borderBottom: "none",
        boxShadow: "none",
        color: "text.primary",
        mt: 0.5,
        borderRadius: "8px",
        height: "40px",
      }}
    >
      <Toolbar>
        <Box
          sx={{
            position: "relative",
            borderRadius: "12px",
            backgroundColor: "rgba(0, 0, 0, 0.04)", // Light grey background for search
            "&:hover": {
              backgroundColor: "rgba(0, 0, 0, 0.06)",
            },
            marginRight: 2,
            width: "100%",
            maxWidth: 400,
          }}
        >
          <Box
            sx={{
              padding: "0 16px",
              height: "100%",
              position: "absolute",
              pointerEvents: "none",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <SearchIcon sx={{ color: "text.secondary" }} />
          </Box>
          <Tooltip title="Search across dashboard, transactions, cases, and reports" arrow>
            <InputBase
              placeholder="Search Dashboard..."
              sx={{
                color: "text.primary",
                width: "100%",
                "& .MuiInputBase-input": {
                  padding: "10px 10px 10px 48px",
                  transition: "width",
                  width: "100%",
                },
              }}
            />
          </Tooltip>
        </Box>

        <Box sx={{ flexGrow: 1 }} />

        <Tooltip title="Your session is currently active and secure" arrow>
          <Chip
            label="Session Active"
            color="success"
            size="small"
            sx={{ mr: 2, borderRadius: "8px", fontWeight: 600 }}
          />
        </Tooltip>

        <Tooltip title="View notifications and alerts" arrow>
          <IconButton color="inherit" sx={{ mr: 1, color: "text.secondary" }}>
            <NotificationsIcon />
          </IconButton>
        </Tooltip>

        <Tooltip title="Click to access your profile and account settings" arrow>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1.5,
              cursor: "pointer",
              px: 1,
              py: 0.5,
              borderRadius: "12px",
              "&:hover": {
                backgroundColor: "rgba(0,0,0,0.03)",
              },
            }}
            onClick={handleProfileClick}
          >
            <Avatar
              sx={{
                width: 36,
                height: 36,
                bgcolor: "primary.main",
                fontSize: "0.9rem",
                fontWeight: 600
              }}
            >
              {userInitials}
            </Avatar>
            <Box sx={{ display: { xs: "none", sm: "block" } }}>
              <Typography variant="subtitle2" sx={{ color: "text.primary", fontWeight: 600, lineHeight: 1.2 }}>
                {userName}
              </Typography>
              <Typography variant="caption" sx={{ color: "text.secondary", fontSize: "0.7rem", display: "block", lineHeight: 1 }}>
                Admin
              </Typography>
            </Box>
          </Box>
        </Tooltip>

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
              borderRadius: "16px",
              boxShadow: "0px 10px 40px rgba(0,0,0,0.1)"
            },
          }}
        >
          <Tooltip title="View and edit your personal profile information" arrow>
            <MenuItem onClick={() => { navigate("/profile"); handleClose(); }}>
              <PersonIcon sx={{ mr: 2, color: "text.secondary" }} />
              My Profile
            </MenuItem>
          </Tooltip>
          <Tooltip title="Configure application settings and preferences" arrow>
            <MenuItem onClick={() => { navigate("/settings"); handleClose(); }}>
              <SettingsIcon sx={{ mr: 2, color: "text.secondary" }} />
              Settings
            </MenuItem>
          </Tooltip>
          <Tooltip title="Sign out of your account" arrow>
            <MenuItem onClick={handleLogout}>
              <LogoutIcon sx={{ mr: 2, color: "text.secondary" }} />
              Logout
            </MenuItem>
          </Tooltip>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
