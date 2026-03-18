import { useState } from "react";
import {
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TablePagination,
    Button,
    Chip,
    IconButton,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
    Switch,
    FormControlLabel,
    Typography,
    Alert,
    Tooltip,
} from "@mui/material";
import {
    Add as AddIcon,
    Edit as EditIcon,
    Delete as DeleteIcon,
    PersonOff as DisableIcon,
    PersonAdd as EnableIcon,
} from "@mui/icons-material";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { User, Role, Psp } from "../../types/userManagement";
import { useUsers } from "../../features/api/queries";

export default function UsersTab() {
    const queryClient = useQueryClient();
    const [openDialog, setOpenDialog] = useState(false);
    const [editingUser, setEditingUser] = useState<User | null>(null);
    const [formData, setFormData] = useState({
        username: "",
        email: "",
        firstName: "",
        lastName: "",
        password: "",
        roleId: "",
        pspId: "",
        enabled: true,
    });

    const [page, setPage] = useState({ index: 0, size: 25 });

    // Fetch users with pagination
    const { data: usersPage, isLoading } = useUsers({
        page: page.index,
        size: page.size,
    });
    
    // Extract users from paginated response
    const users = usersPage?.content || [];

    // Fetch roles for dropdown
    const { data: roles } = useQuery<Role[]>({
        queryKey: ["roles"],
        queryFn: async () => {
            const response = await fetch("/api/v1/roles");
            if (!response.ok) throw new Error("Failed to fetch roles");
            return response.json();
        },
    });

    // Fetch PSPs for dropdown
    const { data: psps } = useQuery<Psp[]>({
        queryKey: ["psps"],
        queryFn: async () => {
            const response = await fetch("/api/v1/psp");
            if (!response.ok) throw new Error("Failed to fetch PSPs");
            return response.json();
        },
    });

    // Create/Update user mutation
    const saveUserMutation = useMutation({
        mutationFn: async (userData: any) => {
            const url = editingUser ? `/api/v1/users/${editingUser.id}` : "/api/v1/users";
            const method = editingUser ? "PUT" : "POST";
            const response = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(userData),
            });
            if (!response.ok) throw new Error("Failed to save user");
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["users"] });
            handleCloseDialog();
        },
    });

    // Delete user mutation
    const deleteUserMutation = useMutation({
        mutationFn: async (userId: number) => {
            const response = await fetch(`/api/v1/users/${userId}`, { method: "DELETE" });
            if (!response.ok) throw new Error("Failed to delete user");
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["users"] });
        },
    });

    // Toggle user enabled status
    const toggleUserMutation = useMutation({
        mutationFn: async ({ userId, enabled }: { userId: number; enabled: boolean }) => {
            const response = await fetch(`/api/v1/users/${userId}/toggle`, {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ enabled }),
            });
            if (!response.ok) throw new Error("Failed to toggle user status");
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["users"] });
        },
    });

    const handleOpenDialog = (user?: User) => {
        if (user) {
            setEditingUser(user);
            setFormData({
                username: user.username,
                email: user.email,
                firstName: user.firstName,
                lastName: user.lastName,
                password: "",
                roleId: user.role.id.toString(),
                pspId: user.psp?.id.toString() || "",
                enabled: user.enabled,
            });
        } else {
            setEditingUser(null);
            setFormData({
                username: "",
                email: "",
                firstName: "",
                lastName: "",
                password: "",
                roleId: "",
                pspId: "",
                enabled: true,
            });
        }
        setOpenDialog(true);
    };

    const handleCloseDialog = () => {
        setOpenDialog(false);
        setEditingUser(null);
    };

    const handleSave = () => {
        const userData: any = {
            username: formData.username,
            email: formData.email,
            firstName: formData.firstName,
            lastName: formData.lastName,
            roleId: parseInt(formData.roleId),
            pspId: formData.pspId ? parseInt(formData.pspId) : null,
            enabled: formData.enabled,
        };

        if (!editingUser || formData.password) {
            userData.password = formData.password;
        }

        saveUserMutation.mutate(userData);
    };

    const handleDelete = (userId: number) => {
        if (window.confirm("Are you sure you want to delete this user?")) {
            deleteUserMutation.mutate(userId);
        }
    };

    const handleToggleEnabled = (userId: number, currentStatus: boolean) => {
        toggleUserMutation.mutate({ userId, enabled: !currentStatus });
    };

    return (
        <Box>
            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
                <Tooltip title="Create a new user account in the system. Opens a form where you can specify the username, email, password, assign a role (which determines permissions), optionally assign to a Payment Service Provider (PSP), and set the account status. The new user will be able to log in immediately if enabled." arrow enterDelay={2000}>
                    <Button
                        variant="contained"
                        startIcon={<AddIcon />}
                        onClick={() => handleOpenDialog()}
                        sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                    >
                        Add User
                    </Button>
                </Tooltip>
            </Box>

            <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Username</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Name</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Email</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Role</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>PSP</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Status</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Created</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={8} align="center" sx={{ py: 2, color: "text.secondary" }}>
                                    Loading users...
                                </TableCell>
                            </TableRow>
                        ) : users && users.length > 0 ? (
                            users.map((user) => (
                                <TableRow key={user.id} hover>
                                    <TableCell sx={{ color: "text.primary", fontWeight: 500, py: 2 }}>{user.username}</TableCell>
                                    <TableCell sx={{ color: "text.primary", py: 2 }}>
                                        {user.firstName} {user.lastName}
                                    </TableCell>
                                    <TableCell sx={{ color: "text.primary", py: 2 }}>{user.email}</TableCell>
                                    <TableCell sx={{ py: 2 }}>
                                        <Chip
                                            label={user.role.name}
                                            size="small"
                                            sx={{
                                                backgroundColor: "#8B404920",
                                                color: "#8B4049",
                                                border: "1px solid #8B4049",
                                                fontWeight: 600,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell sx={{ color: "text.primary", py: 2 }}>{user.psp?.name || "System"}</TableCell>
                                    <TableCell sx={{ py: 2 }}>
                                        <Chip
                                            label={user.enabled ? "Active" : "Disabled"}
                                            size="small"
                                            sx={{
                                                backgroundColor: user.enabled ? "#2ecc7120" : "#95a5a620",
                                                color: user.enabled ? "#2ecc71" : "#95a5a6",
                                                border: `1px solid ${user.enabled ? "#2ecc71" : "#95a5a6"}`,
                                                fontWeight: 600,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell sx={{ color: "text.secondary", fontSize: "0.875rem", py: 2 }}>
                                        {new Date(user.createdAt).toLocaleDateString()}
                                    </TableCell>
                                    <TableCell sx={{ py: 2 }}>
                                        <Box sx={{ display: "flex", gap: 0.5 }}>
                                            <Tooltip title="Edit this user's account details including name, email, role assignment, PSP assignment, and account status. Opens the user edit dialog with pre-filled information. Note: Username cannot be changed after account creation." arrow enterDelay={2000}>
                                                <IconButton size="small" onClick={() => handleOpenDialog(user)} sx={{ color: "#8B4049" }}>
                                                    <EditIcon fontSize="small" />
                                                </IconButton>
                                            </Tooltip>
                                            <Tooltip title={user.enabled ? "Disable this user account to prevent login access. The user will not be able to authenticate until the account is re-enabled. Useful for temporary access suspension or security measures." : "Enable this user account to restore login access. The user will be able to authenticate and access the system based on their assigned role and permissions."} arrow enterDelay={2000}>
                                                <IconButton
                                                    size="small"
                                                    onClick={() => handleToggleEnabled(user.id, user.enabled)}
                                                    sx={{ color: user.enabled ? "#f39c12" : "#2ecc71" }}
                                                >
                                                    {user.enabled ? <DisableIcon fontSize="small" /> : <EnableIcon fontSize="small" />}
                                                </IconButton>
                                            </Tooltip>
                                            <Tooltip title="Permanently delete this user account from the system. This action cannot be undone and will remove all user data, access permissions, and account history. Use with caution. Ensure you have proper authorization before deleting user accounts." arrow enterDelay={2000}>
                                                <IconButton size="small" onClick={() => handleDelete(user.id)} sx={{ color: "#e74c3c" }}>
                                                    <DeleteIcon fontSize="small" />
                                                </IconButton>
                                            </Tooltip>
                                        </Box>
                                    </TableCell>
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={8} align="center" sx={{ py: 2, color: "text.secondary" }}>
                                    No users found
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
                <TablePagination
                  rowsPerPageOptions={[10, 25, 50, 100]}
                  component="div"
                  count={usersPage?.totalElements || 0}
                  rowsPerPage={page.size}
                  page={page.index}
                  onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
                  onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
                />
            </TableContainer>

            {/* Create/Edit User Dialog */}
            <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
                <DialogTitle>{editingUser ? "Edit User" : "Create New User"}</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 2 }}>
                        {saveUserMutation.isError && (
                            <Alert severity="error">Failed to save user. Please try again.</Alert>
                        )}

                        <Tooltip title="Enter a unique username that will be used for login authentication. This username must be unique across the system and cannot be changed after the user account is created. Choose a username that follows your organization's naming convention (e.g., firstname.lastname or employee ID)." arrow placement="top" enterDelay={2000}>
                            <TextField
                                label="Username"
                                value={formData.username}
                                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                                fullWidth
                                required
                                disabled={!!editingUser}
                            />
                        </Tooltip>

                        <Box sx={{ display: "flex", gap: 2 }}>
                            <Tooltip title="Enter the user's legal first name as it should appear in the system. This name will be displayed in user lists, audit logs, and case assignments. Use the person's official first name for consistency." arrow placement="top" enterDelay={2000}>
                                <TextField
                                    label="First Name"
                                    value={formData.firstName}
                                    onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                                    fullWidth
                                    required
                                />
                            </Tooltip>
                            <Tooltip title="Enter the user's legal last name (surname) as it should appear in the system. This name will be displayed alongside the first name in user lists, audit logs, and case assignments. Use the person's official last name for consistency." arrow placement="top" enterDelay={2000}>
                                <TextField
                                    label="Last Name"
                                    value={formData.lastName}
                                    onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                                    fullWidth
                                    required
                                />
                            </Tooltip>
                        </Box>

                        <Tooltip title="Enter a valid email address for this user. This email will be used for system notifications, password reset requests, account recovery, and important alerts. The email must be unique in the system and follow standard email format (e.g., user@example.com)." arrow placement="top" enterDelay={2000}>
                            <TextField
                                label="Email"
                                type="email"
                                value={formData.email}
                                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                                fullWidth
                                required
                            />
                        </Tooltip>

                        <Tooltip title={editingUser ? "Enter a new password to change the user's current password, or leave this field blank to keep the existing password unchanged. The new password should meet security requirements (minimum length, complexity)." : "Enter a secure password for the new user account. The password should be strong (minimum 8 characters, include uppercase, lowercase, numbers, and special characters) to ensure account security. The user will use this password to log in."} arrow placement="top" enterDelay={2000}>
                            <TextField
                                label={editingUser ? "New Password (leave blank to keep current)" : "Password"}
                                type="password"
                                value={formData.password}
                                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                                fullWidth
                                required={!editingUser}
                            />
                        </Tooltip>

                        <Tooltip title="Select the role that determines this user's permissions and access levels in the system. Roles define what actions the user can perform (e.g., view cases, create alerts, manage users). System roles apply globally, while PSP-specific roles are scoped to a particular Payment Service Provider. Choose the role that matches the user's responsibilities." arrow placement="top" enterDelay={2000}>
                            <FormControl fullWidth required>
                                <InputLabel>Role</InputLabel>
                                <Select
                                    value={formData.roleId}
                                    onChange={(e) => setFormData({ ...formData, roleId: e.target.value })}
                                    label="Role"
                                >
                                    {roles?.map((role) => (
                                        <MenuItem key={role.id} value={role.id.toString()}>
                                            {role.name} {role.psp ? `(${role.psp.name})` : "(System)"}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </Tooltip>

                        <Tooltip title="Optionally assign this user to a specific Payment Service Provider (PSP) to restrict their access to that PSP's data only. Leave empty for system-wide access (Super Admin). PSP users can only view and manage data belonging to their assigned PSP, ensuring data isolation and multi-tenancy compliance. System users (no PSP) have access to all data across all PSPs." arrow placement="top" enterDelay={2000}>
                            <FormControl fullWidth>
                                <InputLabel>PSP (Optional)</InputLabel>
                                <Select
                                    value={formData.pspId}
                                    onChange={(e) => setFormData({ ...formData, pspId: e.target.value })}
                                    label="PSP (Optional)"
                                >
                                    <MenuItem value="">None (System User)</MenuItem>
                                    {psps?.map((psp) => (
                                        <MenuItem key={psp.id} value={psp.id.toString()}>
                                            {psp.name}
                                        </MenuItem>
                                    ))}
                                </Select>
                            </FormControl>
                        </Tooltip>

                        <Tooltip title="Enable or disable this user account. When enabled, the user can log in and access the system based on their assigned role and permissions. When disabled, the user cannot authenticate or access the system, but their account data is preserved. Useful for temporary access suspension or when an employee leaves the organization." arrow placement="top" enterDelay={2000}>
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={formData.enabled}
                                        onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
                                    />
                                }
                                label="Enabled"
                            />
                        </Tooltip>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Tooltip title="Cancel the user creation or editing process and discard all changes made in this dialog. Returns to the user list without saving any modifications." arrow enterDelay={2000}>
                        <Button onClick={handleCloseDialog}>Cancel</Button>
                    </Tooltip>
                    <Tooltip title="Save the user information and apply all changes. For new users, this creates the account with the specified details. For existing users, this updates their information including name, email, role, PSP assignment, and account status. The user will be able to log in immediately after creation (if enabled)." arrow enterDelay={2000}>
                        <span>
                            <Button
                                onClick={handleSave}
                                variant="contained"
                                disabled={saveUserMutation.isPending}
                                sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                            >
                                {saveUserMutation.isPending ? "Saving..." : "Save"}
                            </Button>
                        </span>
                    </Tooltip>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

