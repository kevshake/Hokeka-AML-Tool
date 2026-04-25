import { useState, useEffect } from "react";
import {
  Box,
  Grid,
  Paper,
  Typography,
  TextField,
  Button,
  Avatar,
  Chip,
  Divider,
  Alert,
  Tab,
  Tabs,
  Card,
  CardContent,
  IconButton,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
} from "@mui/material";
import {
  Person as PersonIcon,
  Lock as LockIcon,
  Visibility,
  VisibilityOff,
  Save as SaveIcon,
  History as HistoryIcon,
} from "@mui/icons-material";
import { useCurrentUser } from "../../features/api/queries";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { PERMISSION_LABELS, Permission } from "../../types/userManagement";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

export default function ProfilePage() {
  const queryClient = useQueryClient();
  const { data: user, isLoading } = useCurrentUser();
  const [tabValue, setTabValue] = useState(0);
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [profileData, setProfileData] = useState({
    firstName: "",
    lastName: "",
    email: "",
  });

  const [passwordData, setPasswordData] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });

  // Update profile mutation
  const updateProfileMutation = useMutation({
    mutationFn: async (data: any) => {
      return apiClient.put("users/me", data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["user", "me"] });
    },
  });

  // Change password mutation
  const changePasswordMutation = useMutation({
    mutationFn: async (data: any) => {
      return apiClient.put("users/me/password", data);
    },
    onSuccess: () => {
      setPasswordData({ currentPassword: "", newPassword: "", confirmPassword: "" });
    },
  });

  // Initialize form when user data loads
  useEffect(() => {
    if (user) {
      setProfileData({
        firstName: user.firstName || "",
        lastName: user.lastName || "",
        email: user.email || "",
      });
    }
  }, [user]);

  const handleUpdateProfile = () => {
    updateProfileMutation.mutate(profileData);
  };

  const handleChangePassword = () => {
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      return;
    }
    changePasswordMutation.mutate({
      currentPassword: passwordData.currentPassword,
      newPassword: passwordData.newPassword,
    });
  };

  const getInitials = () => {
    if (!user) return "?";
    return `${user.firstName?.[0] || ""}${user.lastName?.[0] || ""}`.toUpperCase();
  };

  if (isLoading) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography sx={{ color: "text.secondary" }}>Loading profile...</Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h6" sx={{ color: "text.primary", mb: 3, fontWeight: 600 }}>
        My Profile
      </Typography>

      {/* Profile Header Card */}
      <Card sx={{ mb: 3, backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 3 }}>
            <Avatar
              sx={{
                width: 80,
                height: 80,
                fontSize: "2rem",
                fontWeight: 700,
                backgroundColor: "#8B4049",
                color: "#fff",
              }}
            >
              {getInitials()}
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
                {user?.firstName} {user?.lastName}
              </Typography>
              <Typography variant="body2" sx={{ color: "text.secondary", mb: 1 }}>
                @{user?.username}
              </Typography>
              <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                <Chip
                  label={user?.role?.name || "No Role"}
                  size="small"
                  sx={{
                    backgroundColor: "#8B404920",
                    color: "#8B4049",
                    border: "1px solid #8B4049",
                  }}
                />
                <Chip
                  label={user?.psp?.name || "System User"}
                  size="small"
                  sx={{
                    backgroundColor: user?.psp ? "#f39c1220" : "#95a5a620",
                    color: user?.psp ? "#f39c12" : "#95a5a6",
                    border: `1px solid ${user?.psp ? "#f39c12" : "#95a5a6"}`,
                  }}
                />
                <Chip
                  label={user?.enabled ? "Active" : "Disabled"}
                  size="small"
                  sx={{
                    backgroundColor: user?.enabled ? "#2ecc7120" : "#e74c3c20",
                    color: user?.enabled ? "#2ecc71" : "#e74c3c",
                    border: `1px solid ${user?.enabled ? "#2ecc71" : "#e74c3c"}`,
                  }}
                />
              </Box>
            </Box>
            <Box sx={{ textAlign: "right" }}>
              <Typography variant="caption" sx={{ color: "text.secondary", display: "block" }}>
                Member Since
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {user?.createdAt ? new Date(user.createdAt).toLocaleDateString() : "N/A"}
              </Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>

      {/* Tabs */}
      <Paper sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Tabs
          value={tabValue}
          onChange={(_, newValue) => setTabValue(newValue)}
          sx={{
            borderBottom: 1,
            borderColor: "divider",
            "& .MuiTab-root": {
              textTransform: "none",
              fontWeight: 500,
              fontSize: "0.9rem",
              color: "text.secondary",
              "&.Mui-selected": {
                color: "#8B4049",
                fontWeight: 600,
              },
            },
            "& .MuiTabs-indicator": {
              backgroundColor: "#8B4049",
              height: 3,
            },
          }}
        >
          <Tooltip title="View and edit your personal information" arrow>
            <Tab icon={<PersonIcon />} iconPosition="start" label="Personal Information" />
          </Tooltip>
          <Tooltip title="Change your password and manage security settings" arrow>
            <Tab icon={<LockIcon />} iconPosition="start" label="Security" />
          </Tooltip>
          <Tooltip title="View your role permissions and access levels" arrow>
            <Tab icon={<HistoryIcon />} iconPosition="start" label="Permissions" />
          </Tooltip>
        </Tabs>

        {/* Personal Information Tab */}
        <TabPanel value={tabValue} index={0}>
          <Box sx={{ p: 3 }}>
            {updateProfileMutation.isSuccess && (
              <Alert severity="success" sx={{ mb: 3 }}>
                Profile updated successfully!
              </Alert>
            )}
            {updateProfileMutation.isError && (
              <Alert severity="error" sx={{ mb: 3 }}>
                Failed to update profile. Please try again.
              </Alert>
            )}

            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Tooltip title="Your username is permanent and cannot be changed" arrow placement="top">
                  <TextField
                    fullWidth
                    label="Username"
                    value={user?.username || ""}
                    disabled
                    helperText="Username cannot be changed"
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12} md={6}>
                <Tooltip title="Update your email address for notifications and account recovery" arrow placement="top">
                  <TextField
                    fullWidth
                    label="Email"
                    type="email"
                    value={profileData.email}
                    onChange={(e) => setProfileData({ ...profileData, email: e.target.value })}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12} md={6}>
                <Tooltip title="Enter your first name" arrow placement="top">
                  <TextField
                    fullWidth
                    label="First Name"
                    value={profileData.firstName}
                    onChange={(e) => setProfileData({ ...profileData, firstName: e.target.value })}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12} md={6}>
                <Tooltip title="Enter your last name" arrow placement="top">
                  <TextField
                    fullWidth
                    label="Last Name"
                    value={profileData.lastName}
                    onChange={(e) => setProfileData({ ...profileData, lastName: e.target.value })}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12}>
                <Divider sx={{ my: 2 }} />
                <Tooltip title="Save your profile changes" arrow placement="top">
                  <span>
                    <Button
                      variant="contained"
                      startIcon={<SaveIcon />}
                      onClick={handleUpdateProfile}
                      disabled={updateProfileMutation.isPending}
                      sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                    >
                      {updateProfileMutation.isPending ? "Saving..." : "Save Changes"}
                    </Button>
                  </span>
                </Tooltip>
              </Grid>
            </Grid>
          </Box>
        </TabPanel>

        {/* Security Tab */}
        <TabPanel value={tabValue} index={1}>
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ mb: 3, fontWeight: 600 }}>
              Change Password
            </Typography>

            {changePasswordMutation.isSuccess && (
              <Alert severity="success" sx={{ mb: 3 }}>
                Password changed successfully!
              </Alert>
            )}
            {changePasswordMutation.isError && (
              <Alert severity="error" sx={{ mb: 3 }}>
                Failed to change password. Please check your current password.
              </Alert>
            )}

            <Grid container spacing={3}>
              <Grid item xs={12}>
                <Tooltip title="Enter your current password to verify your identity" arrow placement="top">
                  <TextField
                    fullWidth
                    label="Current Password"
                    type={showPassword ? "text" : "password"}
                    value={passwordData.currentPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                    InputProps={{
                      endAdornment: (
                        <InputAdornment position="end">
                          <Tooltip title={showPassword ? "Hide password" : "Show password"} arrow>
                            <IconButton onClick={() => setShowPassword(!showPassword)} edge="end">
                              {showPassword ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </Tooltip>
                        </InputAdornment>
                      ),
                    }}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12} md={6}>
                <Tooltip title="Enter your new secure password. Use a combination of letters, numbers, and special characters." arrow placement="top">
                  <TextField
                    fullWidth
                    label="New Password"
                    type={showNewPassword ? "text" : "password"}
                    value={passwordData.newPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                    InputProps={{
                      endAdornment: (
                        <InputAdornment position="end">
                          <Tooltip title={showNewPassword ? "Hide password" : "Show password"} arrow>
                            <IconButton onClick={() => setShowNewPassword(!showNewPassword)} edge="end">
                              {showNewPassword ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </Tooltip>
                        </InputAdornment>
                      ),
                    }}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12} md={6}>
                <Tooltip title="Re-enter your new password to confirm it matches" arrow placement="top">
                  <TextField
                    fullWidth
                    label="Confirm New Password"
                    type={showConfirmPassword ? "text" : "password"}
                    value={passwordData.confirmPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, confirmPassword: e.target.value })}
                    error={passwordData.newPassword !== passwordData.confirmPassword && passwordData.confirmPassword !== ""}
                    helperText={
                      passwordData.newPassword !== passwordData.confirmPassword && passwordData.confirmPassword !== ""
                        ? "Passwords do not match"
                        : ""
                    }
                    InputProps={{
                      endAdornment: (
                        <InputAdornment position="end">
                          <Tooltip title={showConfirmPassword ? "Hide password" : "Show password"} arrow>
                            <IconButton onClick={() => setShowConfirmPassword(!showConfirmPassword)} edge="end">
                              {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                            </IconButton>
                          </Tooltip>
                        </InputAdornment>
                      ),
                    }}
                  />
                </Tooltip>
              </Grid>
              <Grid item xs={12}>
                <Divider sx={{ my: 2 }} />
                <Tooltip title="Change your account password. All fields must be filled and passwords must match." arrow placement="top">
                  <span>
                    <Button
                      variant="contained"
                      startIcon={<LockIcon />}
                      onClick={handleChangePassword}
                      disabled={
                        changePasswordMutation.isPending ||
                        !passwordData.currentPassword ||
                        !passwordData.newPassword ||
                        passwordData.newPassword !== passwordData.confirmPassword
                      }
                      sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                    >
                      {changePasswordMutation.isPending ? "Changing..." : "Change Password"}
                    </Button>
                  </span>
                </Tooltip>
              </Grid>
            </Grid>
          </Box>
        </TabPanel>

        {/* Permissions Tab */}
        <TabPanel value={tabValue} index={2}>
          <Box sx={{ p: 3 }}>
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
              Your Permissions
            </Typography>
            <Typography variant="body2" sx={{ color: "text.secondary", mb: 3 }}>
              These are the permissions granted to your role: <strong>{user?.role?.name}</strong>
            </Typography>

            {user?.role?.permissions && user.role.permissions.length > 0 ? (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>Permission</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {user.role.permissions.map((permission) => (
                      <TableRow key={permission}>
                        <TableCell>{PERMISSION_LABELS[permission as Permission] || permission}</TableCell>
                        <TableCell>
                          <Chip
                            label="Granted"
                            size="small"
                            sx={{
                              backgroundColor: "#2ecc7120",
                              color: "#2ecc71",
                              border: "1px solid #2ecc71",
                            }}
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Alert severity="info">No permissions assigned to your role.</Alert>
            )}
          </Box>
        </TabPanel>
      </Paper>
    </Box>
  );
}

