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
    Typography,
    Alert,
    Accordion,
    AccordionSummary,
    AccordionDetails,
    FormGroup,
    FormControlLabel,
    Checkbox,
} from "@mui/material";
import {
    Add as AddIcon,
    Edit as EditIcon,
    Delete as DeleteIcon,
    ExpandMore as ExpandMoreIcon,
} from "@mui/icons-material";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Role, Psp, Permission, PERMISSION_LABELS, PERMISSION_CATEGORIES } from "../../types/userManagement";

export default function RolesTab() {
    const queryClient = useQueryClient();
    const [openDialog, setOpenDialog] = useState(false);
    const [editingRole, setEditingRole] = useState<Role | null>(null);
    const [formData, setFormData] = useState({
        name: "",
        description: "",
        pspId: "",
        permissions: [] as Permission[],
    });

    // Fetch roles
    const { data: roles, isLoading } = useQuery<Role[]>({
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

    // Create/Update role mutation
    const saveRoleMutation = useMutation({
        mutationFn: async (roleData: any) => {
            const url = editingRole ? `/api/v1/roles/${editingRole.id}` : "/api/v1/roles";
            const method = editingRole ? "PUT" : "POST";
            const response = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(roleData),
            });
            if (!response.ok) throw new Error("Failed to save role");
            return response.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["roles"] });
            handleCloseDialog();
        },
    });

    // Delete role mutation
    const deleteRoleMutation = useMutation({
        mutationFn: async (roleId: number) => {
            const response = await fetch(`/api/v1/roles/${roleId}`, { method: "DELETE" });
            if (!response.ok) throw new Error("Failed to delete role");
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ["roles"] });
        },
    });

    const handleOpenDialog = (role?: Role) => {
        if (role) {
            setEditingRole(role);
            setFormData({
                name: role.name,
                description: role.description,
                pspId: role.psp?.id.toString() || "",
                permissions: role.permissions,
            });
        } else {
            setEditingRole(null);
            setFormData({
                name: "",
                description: "",
                pspId: "",
                permissions: [],
            });
        }
        setOpenDialog(true);
    };

    const handleCloseDialog = () => {
        setOpenDialog(false);
        setEditingRole(null);
    };

    const handleSave = () => {
        const roleData = {
            name: formData.name,
            description: formData.description,
            pspId: formData.pspId ? parseInt(formData.pspId) : null,
            permissions: formData.permissions,
        };

        saveRoleMutation.mutate(roleData);
    };

    const handleDelete = (roleId: number) => {
        if (window.confirm("Are you sure you want to delete this role? Users with this role will need to be reassigned.")) {
            deleteRoleMutation.mutate(roleId);
        }
    };

    const handlePermissionToggle = (permission: Permission) => {
        setFormData((prev) => ({
            ...prev,
            permissions: prev.permissions.includes(permission)
                ? prev.permissions.filter((p) => p !== permission)
                : [...prev.permissions, permission],
        }));
    };

    const handleCategoryToggle = (category: string, categoryPermissions: Permission[]) => {
        const allSelected = categoryPermissions.every((p) => formData.permissions.includes(p));
        setFormData((prev) => ({
            ...prev,
            permissions: allSelected
                ? prev.permissions.filter((p) => !categoryPermissions.includes(p))
                : [...new Set([...prev.permissions, ...categoryPermissions])],
        }));
    };

    return (
        <Box>
            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 3 }}>
                <Button
                    variant="contained"
                    startIcon={<AddIcon />}
                    onClick={() => handleOpenDialog()}
                    sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                >
                    Create Role
                </Button>
            </Box>

            <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Role Name</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Description</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Scope</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Permissions</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={5} align="center" sx={{ py: 4, color: "text.secondary" }}>
                                    Loading roles...
                                </TableCell>
                            </TableRow>
                        ) : roles && roles.length > 0 ? (
                            roles.map((role) => (
                                <TableRow key={role.id} hover>
                                    <TableCell sx={{ color: "text.primary", fontWeight: 500 }}>{role.name}</TableCell>
                                    <TableCell sx={{ color: "text.primary" }}>{role.description}</TableCell>
                                    <TableCell>
                                        <Chip
                                            label={role.psp ? role.psp.name : "System"}
                                            size="small"
                                            sx={{
                                                backgroundColor: role.psp ? "#f39c1220" : "#8B404920",
                                                color: role.psp ? "#f39c12" : "#8B4049",
                                                border: `1px solid ${role.psp ? "#f39c12" : "#8B4049"}`,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Typography variant="body2" sx={{ color: "text.secondary" }}>
                                            {role.permissions.length} permission{role.permissions.length !== 1 ? "s" : ""}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Box sx={{ display: "flex", gap: 0.5 }}>
                                            <IconButton size="small" onClick={() => handleOpenDialog(role)} sx={{ color: "#8B4049" }}>
                                                <EditIcon fontSize="small" />
                                            </IconButton>
                                            <IconButton size="small" onClick={() => handleDelete(role.id)} sx={{ color: "#e74c3c" }}>
                                                <DeleteIcon fontSize="small" />
                                            </IconButton>
                                        </Box>
                                    </TableCell>
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={5} align="center" sx={{ py: 4, color: "text.secondary" }}>
                                    No roles found
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>

            {/* Create/Edit Role Dialog */}
            <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="md" fullWidth>
                <DialogTitle>{editingRole ? "Edit Role" : "Create New Role"}</DialogTitle>
                <DialogContent>
                    <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 2 }}>
                        {saveRoleMutation.isError && (
                            <Alert severity="error">Failed to save role. Please try again.</Alert>
                        )}

                        <TextField
                            label="Role Name"
                            value={formData.name}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                            fullWidth
                            required
                            placeholder="e.g., Compliance Officer, Analyst"
                        />

                        <TextField
                            label="Description"
                            value={formData.description}
                            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                            fullWidth
                            multiline
                            rows={2}
                            placeholder="Brief description of this role's responsibilities"
                        />

                        <FormControl fullWidth>
                            <InputLabel>PSP Scope (Optional)</InputLabel>
                            <Select
                                value={formData.pspId}
                                onChange={(e) => setFormData({ ...formData, pspId: e.target.value })}
                                label="PSP Scope (Optional)"
                            >
                                <MenuItem value="">System Role (Global)</MenuItem>
                                {psps?.map((psp) => (
                                    <MenuItem key={psp.id} value={psp.id.toString()}>
                                        {psp.name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>

                        <Box>
                            <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
                                Permissions ({formData.permissions.length} selected)
                            </Typography>

                            {Object.entries(PERMISSION_CATEGORIES).map(([category, categoryPermissions]) => {
                                const allSelected = categoryPermissions.every((p) => formData.permissions.includes(p));
                                const someSelected = categoryPermissions.some((p) => formData.permissions.includes(p));

                                return (
                                    <Accordion key={category} sx={{ mb: 1 }}>
                                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        checked={allSelected}
                                                        indeterminate={someSelected && !allSelected}
                                                        onChange={() => handleCategoryToggle(category, categoryPermissions)}
                                                        onClick={(e) => e.stopPropagation()}
                                                    />
                                                }
                                                label={
                                                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                                                        {category}
                                                    </Typography>
                                                }
                                                onClick={(e) => e.stopPropagation()}
                                            />
                                        </AccordionSummary>
                                        <AccordionDetails>
                                            <FormGroup>
                                                {categoryPermissions.map((permission) => (
                                                    <FormControlLabel
                                                        key={permission}
                                                        control={
                                                            <Checkbox
                                                                checked={formData.permissions.includes(permission)}
                                                                onChange={() => handlePermissionToggle(permission)}
                                                            />
                                                        }
                                                        label={PERMISSION_LABELS[permission]}
                                                    />
                                                ))}
                                            </FormGroup>
                                        </AccordionDetails>
                                    </Accordion>
                                );
                            })}
                        </Box>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseDialog}>Cancel</Button>
                    <Button
                        onClick={handleSave}
                        variant="contained"
                        disabled={saveRoleMutation.isPending || !formData.name}
                        sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}
                    >
                        {saveRoleMutation.isPending ? "Saving..." : "Save"}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

