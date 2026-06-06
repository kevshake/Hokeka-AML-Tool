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
    const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
    const [formData, setFormData] = useState({
        name: "",
        description: "",
        pspId: "",
        permissions: [] as Permission[],
    });

    // Helper — every API call must carry the session cookie so Spring Security
    // sees the authenticated principal. Without `credentials: include` the request
    // is anonymous and the @PreAuthorize check returns 403, surfacing as
    // "Failed to save role".
    const apiFetch = async (input: string, init: RequestInit = {}) => {
        const response = await fetch(input, {
            ...init,
            credentials: "include",
            headers: {
                "Content-Type": "application/json",
                ...(init.headers || {}),
            },
        });
        if (!response.ok) {
            let message = `${response.status} ${response.statusText}`;
            try {
                const body = await response.clone().json();
                message = body.message || body.error || JSON.stringify(body);
            } catch {
                try {
                    const txt = await response.text();
                    if (txt) message = txt;
                } catch { /* ignore */ }
            }
            const err = new Error(message);
            (err as any).status = response.status;
            throw err;
        }
        return response;
    };

    // Fetch roles
    const { data: roles, isLoading } = useQuery<Role[]>({
        queryKey: ["roles"],
        queryFn: async () => (await apiFetch("/api/v1/roles")).json(),
    });

    // Fetch PSPs for dropdown — admin endpoint returns full Psp entity (pspId/legalName/pspCode)
    const { data: psps } = useQuery<Psp[]>({
        queryKey: ["psps"],
        queryFn: async () => {
            const response = await fetch("/api/v1/psps");
            if (!response.ok) throw new Error("Failed to fetch PSPs");
            return response.json();
        },
    });

    // Create/Update role mutation
    const saveRoleMutation = useMutation({
        mutationFn: async (roleData: any) => {
            const url = editingRole ? `/api/v1/roles/${editingRole.id}` : "/api/v1/roles";
            const method = editingRole ? "PUT" : "POST";
            const response = await apiFetch(url, {
                method,
                body: JSON.stringify(roleData),
            });
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
        await apiFetch(`/api/v1/roles/${roleId}`, { method: "DELETE" });
    },
    onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: () => {
        alert("Failed to delete role. Please try again.");
    },
});

    const handleOpenDialog = (role?: Role) => {
        if (role) {
            setEditingRole(role);
            const scopedPspId = role.psp?.pspId ?? role.psp?.id ?? role.pspId;
            setFormData({
                name: role.name,
                description: role.description,
                pspId: scopedPspId != null ? String(scopedPspId) : "",
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
    if (!formData.name) return;
    const roleData = {
        name: formData.name,
            description: formData.description,
            pspId: formData.pspId ? parseInt(formData.pspId) : null,
            permissions: formData.permissions,
        };

        saveRoleMutation.mutate(roleData);
    };

 // ✅ Replace with
const handleDelete = (roleId: number) => {
    setDeleteConfirmId(roleId);
};

const handleConfirmDelete = () => {
    if (deleteConfirmId !== null) {
        deleteRoleMutation.mutate(deleteConfirmId);
        setDeleteConfirmId(null);
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

    const handleCategoryToggle = (_category: string, categoryPermissions: Permission[]) => {
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
                                        {(() => {
                                            const scopeLabel = role.psp
                                                ? (role.psp.legalName || role.psp.name || role.psp.pspCode || role.psp.code || "PSP")
                                                : "System";
                                            const scoped = !!role.psp;
                                            return (
                                                <Chip
                                                    label={scopeLabel}
                                                    size="small"
                                                    sx={{
                                                        backgroundColor: scoped ? "#f39c1220" : "#8B404920",
                                                        color: scoped ? "#f39c12" : "#8B4049",
                                                        border: `1px solid ${scoped ? "#f39c12" : "#8B4049"}`,
                                                    }}
                                                />
                                            );
                                        })()}
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
                                            <IconButton size="small" onClick={() => handleDelete(role.id)} sx={{ color: "error.main" }}>
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
                            <Alert severity="error">
                                {(saveRoleMutation.error as Error)?.message || "Failed to save role. Please try again."}
                            </Alert>
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
                                {psps?.map((psp) => {
                                    const id = psp.pspId ?? psp.id;
                                    if (id == null) return null;
                                    const label = psp.legalName || psp.name || psp.pspCode || psp.code || `PSP ${id}`;
                                    return (
                                        <MenuItem key={id} value={String(id)}>
                                            {label}
                                        </MenuItem>
                                    );
                                })}
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
        
            {/* Delete Confirmation Dialog - only ONE instance */}
            <Dialog open={deleteConfirmId !== null} onClose={() => setDeleteConfirmId(null)}>
                <DialogTitle>Delete Role</DialogTitle>
                <DialogContent>
                    Are you sure you want to delete this role? Users with this role will need to be reassigned.
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDeleteConfirmId(null)}>Cancel</Button>
                    <Button
                        onClick={handleConfirmDelete}
                        variant="contained"
                        sx={{ backgroundColor: "error.main", "&:hover": { backgroundColor: "error.dark" } }}
                    >
                        Delete
                    </Button>
                </DialogActions>
            </Dialog>

        </Box> 
    );
}



    
