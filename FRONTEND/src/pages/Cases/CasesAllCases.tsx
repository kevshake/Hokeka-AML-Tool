import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
    Box,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Chip,
    Button,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Tooltip,
    TablePagination,
    CircularProgress,
    Avatar,
    Typography,
    Stack,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Alert,
    Divider,
    Grid,
} from "@mui/material";
import { useCase, useCases } from "../../features/api/queries";
import { useCreateCase } from "../../features/api/mutations";
import { useAuth } from "../../contexts/AuthContext";
import type { ApiError } from "../../lib/apiClient";
import type { Case, Priority } from "../../types";

const statusConfig: Record<string, { color: string; bgColor: string; label: string }> = {
    NEW: { color: "var(--info)", bgColor: "var(--surface-3)", label: "New" },
    ASSIGNED: { color: "var(--gold)", bgColor: "var(--surface-3)", label: "Assigned" },
    INVESTIGATING: { color: "#b094c2", bgColor: "var(--surface-3)", label: "Investigating" },
    PENDING_REVIEW: { color: "var(--danger)", bgColor: "var(--surface-3)", label: "Pending Review" },
    RESOLVED: { color: "var(--success)", bgColor: "var(--surface-3)", label: "Resolved" },
    ESCALATED: { color: "var(--gold)", bgColor: "var(--surface-3)", label: "Escalated" },
};

const priorityConfig: Record<string, { color: string; bgColor: string; label: string }> = {
    CRITICAL: { color: "var(--danger)", bgColor: "var(--surface-3)", label: "CRITICAL" },
    HIGH: { color: "var(--risk-high)", bgColor: "var(--surface-3)", label: "HIGH" },
    MEDIUM: { color: "var(--warning)", bgColor: "var(--surface-3)", label: "MEDIUM" },
    LOW: { color: "var(--muted)", bgColor: "var(--surface-2)", label: "LOW" },
};

export default function CasesAllCases() {
    const [searchParams, setSearchParams] = useSearchParams();
    const deepLinkCaseId = Number(searchParams.get("caseId") || 0);
    const [statusFilter, setStatusFilter] = useState<string>("");
    const [page, setPage] = useState({ index: 0, size: 25 });
    const [createOpen, setCreateOpen] = useState(false);
    const [newCase, setNewCase] = useState({ caseReference: "", description: "", priority: "MEDIUM" as Priority });
    const [viewCase, setViewCase] = useState<Case | null>(null);

    const { user } = useAuth();
    const { data: cases, isLoading, isError, error } = useCases({
        page: page.index,
        size: page.size,
        status: statusFilter || undefined,
    });
    const deepLinkedCase = useCase(deepLinkCaseId);

    const createCase = useCreateCase();

    useEffect(() => {
        if (!deepLinkCaseId) return;
        const fromPage = cases?.content?.find((c) => c.id === deepLinkCaseId);
        if (fromPage) {
            setViewCase(fromPage);
            return;
        }
        if (deepLinkedCase.data) {
            setViewCase(deepLinkedCase.data);
        }
    }, [deepLinkCaseId, cases?.content, deepLinkedCase.data]);

    const handleExportCSV = () => {
        const content = cases?.content || [];
        if (!content.length) return;
        const headers = ["Reference", "Status", "Priority", "Description", "Assigned To", "Created", "Days Open"];
        const rows = content.map(c => [
            c.caseReference,
            c.status,
            c.priority,
            (c.description || "").replace(/,/g, ";"),
            c.assignedTo ? (c.assignedTo.firstName || c.assignedTo.username || "") : "Unassigned",
            new Date(c.createdAt).toISOString().split("T")[0],
            String(c.daysOpen ?? ""),
        ]);
        const csv = [headers, ...rows].map(r => r.join(",")).join("\n");
        const blob = new Blob([csv], { type: "text/csv" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `cases-${new Date().toISOString().split("T")[0]}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    const handleCreateCase = () => {
        if (!newCase.caseReference.trim()) return;
        createCase.mutate({ ...newCase, creatorUserId: user?.id }, {
            onSuccess: () => {
                setCreateOpen(false);
                setNewCase({ caseReference: "", description: "", priority: "MEDIUM" });
            },
        });
    };

    return (
        <Box sx={{ mt: 1 }}>
            <Box sx={{ 
                display: "flex", 
                justifyContent: "space-between", 
                alignItems: "center", 
                mb: 3,
                pb: 2,
                borderBottom: "1px solid",
                borderColor: "divider",
            }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Typography variant="body2" color="text.secondary">
                        {cases?.totalElements || 0} cases total
                    </Typography>
                    <Button
                        size="small"
                        variant="outlined"
                        disabled={!cases?.content?.length}
                        onClick={handleExportCSV}
                        sx={{ textTransform: "none", color: "text.secondary", borderColor: "rgba(0,0,0,0.2)", fontSize: "0.75rem" }}
                    >
                        Export CSV
                    </Button>
                </Box>
                
                <Box sx={{ display: "flex", gap: 2 }}>
                    <Tooltip title="Filter the cases list by their current workflow status." arrow placement="top">
                        <FormControl size="small" sx={{ minWidth: 180 }}>
                            <InputLabel sx={{ color: "text.secondary" }}>Filter by Status</InputLabel>
                            <Select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                label="Filter by Status"
                                sx={{
                                    color: "text.primary",
                                    "& .MuiOutlinedInput-notchedOutline": {
                                        borderColor: "rgba(0,0,0,0.15)",
                                    },
                                    "&:hover .MuiOutlinedInput-notchedOutline": {
                                        borderColor: "rgba(0,0,0,0.3)",
                                    },
                                }}
                            >
                                <MenuItem value="">All Statuses</MenuItem>
                                <MenuItem value="NEW">New</MenuItem>
                                <MenuItem value="ASSIGNED">Assigned</MenuItem>
                                <MenuItem value="INVESTIGATING">Investigating</MenuItem>
                                <MenuItem value="PENDING_REVIEW">Pending Review</MenuItem>
                                <MenuItem value="RESOLVED">Resolved</MenuItem>
                                <MenuItem value="ESCALATED">Escalated</MenuItem>
                            </Select>
                        </FormControl>
                    </Tooltip>
                    <Button
                        variant="contained"
                        onClick={() => setCreateOpen(true)}
                        sx={{
                            backgroundColor: "var(--surface-3)",
                            "&:hover": { backgroundColor: "var(--surface-3)" },
                            textTransform: "none",
                            borderRadius: 1.5,
                        }}
                    >
                        Create Case
                    </Button>
                </Box>
            </Box>

            <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
                <Table size="small" sx={{ tableLayout: "fixed" }}>
                    <TableHead>
                        <TableRow sx={{ backgroundColor: "var(--surface-3)" }}>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 140, whiteSpace: "nowrap" }}>Reference</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 120, whiteSpace: "nowrap" }}>Status</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 100, whiteSpace: "nowrap" }}>Priority</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: "25%" }}>Description</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 130, whiteSpace: "nowrap" }}>Assigned To</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 100, whiteSpace: "nowrap" }}>Created</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600, width: 80, whiteSpace: "nowrap" }}>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 6 }}>
                                    <CircularProgress size={32} sx={{ color: "var(--gold)" }} />
                                </TableCell>
                            </TableRow>
                        ) : isError ? (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "var(--danger)", py: 6 }}>
                                    Error loading cases: {error instanceof Error ? error.message : "Unknown error"}
                                </TableCell>
                            </TableRow>
                        ) : cases?.content && cases.content.length > 0 ? (
                            cases.content.map((caseItem) => (
                                <TableRow key={caseItem.id} hover sx={{ cursor: "pointer", "&:hover": { backgroundColor: "var(--surface-2)" } }}>
                                    <TableCell sx={{ color: "text.primary", fontWeight: 500, whiteSpace: "nowrap" }}>
                                        <Typography variant="body2" sx={{ fontFamily: "monospace", fontWeight: 600 }}>
                                            {caseItem.caseReference}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            label={statusConfig[caseItem.status]?.label || caseItem.status}
                                            size="small"
                                            sx={{
                                                backgroundColor: statusConfig[caseItem.status]?.bgColor || "var(--surface-3)",
                                                color: statusConfig[caseItem.status]?.color || "#666",
                                                fontWeight: 500,
                                                fontSize: "0.75rem",
                                                borderRadius: 1,
                                                height: 24,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            label={priorityConfig[caseItem.priority]?.label || caseItem.priority}
                                            size="small"
                                            sx={{
                                                backgroundColor: priorityConfig[caseItem.priority]?.bgColor || "var(--surface-3)",
                                                color: priorityConfig[caseItem.priority]?.color || "#666",
                                                fontWeight: 600,
                                                fontSize: "0.7rem",
                                                borderRadius: 1,
                                                height: 22,
                                                letterSpacing: "0.3px",
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell sx={{ color: "text.primary" }}>
                                        <Typography variant="body2" sx={{ 
                                            overflow: "hidden", 
                                            textOverflow: "ellipsis", 
                                            display: "-webkit-box",
                                            WebkitLineClamp: 2,
                                            WebkitBoxOrient: "vertical",
                                            lineHeight: 1.4,
                                        }}>
                                            {caseItem.description}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Stack direction="row" alignItems="center" spacing={1}>
                                            <Avatar sx={{ width: 24, height: 24, fontSize: "0.75rem", backgroundColor: "var(--surface-3)" }}>
                                                {(caseItem.assignedTo?.username || "U").charAt(0).toUpperCase()}
                                            </Avatar>
                                            <Typography variant="body2" sx={{ color: "text.primary" }}>
                                                {caseItem.assignedTo?.username || "Unassigned"}
                                            </Typography>
                                        </Stack>
                                    </TableCell>
                                    <TableCell sx={{ color: "text.secondary", whiteSpace: "nowrap" }}>
                                        <Typography variant="caption">
                                            {new Date(caseItem.createdAt).toLocaleDateString(undefined, { 
                                                month: "short", 
                                                day: "numeric",
                                                year: "numeric",
                                            })}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Button 
                                            size="small" 
                                            variant="outlined"
                                            sx={{ 
                                                color: "var(--gold)", 
                                                borderColor: "var(--gold)",
                                                textTransform: "none",
                                                minWidth: "unset",
                                                px: 1.5,
                                                py: 0.5,
                                                fontSize: "0.75rem",
                                                "&:hover": { 
                                                    backgroundColor: "var(--surface-3)",
                                                    borderColor: "var(--gold)",
                                                }
                                            }}
                                            onClick={() => setViewCase(caseItem)}
                                        >
                                            View
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 8 }}>
                                    <Typography variant="body1">No cases found</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
                <TablePagination
                    rowsPerPageOptions={[10, 25, 50, 100]}
                    component="div"
                    count={cases?.totalElements || 0}
                    rowsPerPage={page.size}
                    page={page.index}
                    onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
                    onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
                    sx={{ 
                        borderTop: "1px solid", 
                        borderColor: "divider",
                        backgroundColor: "var(--surface-3)",
                    }}
                />
            </TableContainer>

            <Dialog open={createOpen} onClose={() => { setCreateOpen(false); createCase.reset(); }} maxWidth="sm" fullWidth>
                <DialogTitle>Create New Case</DialogTitle>
                <DialogContent sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
                    {createCase.isError && (
                        <Alert severity="error">
                            {(() => {
                                const err = createCase.error as ApiError | Error | null;
                                if (err && "status" in err) {
                                    if (err.status === 503) return "Service temporarily unavailable. Please try again in a moment.";
                                    if (err.status === 403) return "You do not have permission to create cases.";
                                    if (err.message) return err.message;
                                }
                                return "Failed to create case. Please try again.";
                            })()}
                        </Alert>
                    )}
                    <TextField
                        label="Case Reference"
                        value={newCase.caseReference}
                        onChange={(e) => setNewCase(prev => ({ ...prev, caseReference: e.target.value }))}
                        required
                        fullWidth
                        size="small"
                    />
                    <TextField
                        label="Description"
                        value={newCase.description}
                        onChange={(e) => setNewCase(prev => ({ ...prev, description: e.target.value }))}
                        fullWidth
                        size="small"
                        multiline
                        rows={3}
                    />
                    <FormControl size="small" fullWidth>
                        <InputLabel>Priority</InputLabel>
                        <Select
                            value={newCase.priority}
                            label="Priority"
                            onChange={(e) => setNewCase(prev => ({ ...prev, priority: e.target.value as Priority }))}
                        >
                            <MenuItem value="CRITICAL">Critical</MenuItem>
                            <MenuItem value="HIGH">High</MenuItem>
                            <MenuItem value="MEDIUM">Medium</MenuItem>
                            <MenuItem value="LOW">Low</MenuItem>
                        </Select>
                    </FormControl>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCreateOpen(false)} sx={{ textTransform: "none" }}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleCreateCase}
                        disabled={!newCase.caseReference.trim() || createCase.isPending}
                        sx={{ backgroundColor: "var(--surface-3)", "&:hover": { backgroundColor: "var(--surface-3)" }, textTransform: "none" }}
                    >
                        {createCase.isPending ? "Creating..." : "Create Case"}
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Case Detail Modal */}
            <Dialog
                open={!!viewCase}
                onClose={() => {
                    setViewCase(null);
                    if (deepLinkCaseId) {
                        const next = new URLSearchParams(searchParams);
                        next.delete("caseId");
                        next.delete("action");
                        setSearchParams(next, { replace: true });
                    }
                }}
                maxWidth="md"
                fullWidth
            >
                <DialogTitle sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <Box>
                        <Typography variant="h6" sx={{ fontFamily: "monospace", fontWeight: 700 }}>
                            {viewCase?.caseReference}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">Case Details</Typography>
                    </Box>
                    <Box sx={{ display: "flex", gap: 1 }}>
                        {viewCase && (
                            <Chip
                                label={statusConfig[viewCase.status]?.label || viewCase.status}
                                size="small"
                                sx={{
                                    backgroundColor: statusConfig[viewCase.status]?.bgColor || "var(--surface-3)",
                                    color: statusConfig[viewCase.status]?.color || "#666",
                                    fontWeight: 600,
                                }}
                            />
                        )}
                        {viewCase && (
                            <Chip
                                label={priorityConfig[viewCase.priority]?.label || viewCase.priority}
                                size="small"
                                sx={{
                                    backgroundColor: priorityConfig[viewCase.priority]?.bgColor || "var(--surface-3)",
                                    color: priorityConfig[viewCase.priority]?.color || "#666",
                                    fontWeight: 600,
                                    fontSize: "0.7rem",
                                }}
                            />
                        )}
                    </Box>
                </DialogTitle>
                <Divider />
                {viewCase && (
                    <DialogContent sx={{ pt: 2 }}>
                        <Grid container spacing={3}>
                            <Grid item xs={12}>
                                <Typography variant="overline" color="text.secondary">Description</Typography>
                                <Typography variant="body2" sx={{ mt: 0.5, color: "text.primary", lineHeight: 1.6 }}>
                                    {viewCase.description || "No description provided."}
                                </Typography>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                                <Typography variant="overline" color="text.secondary">Assigned To</Typography>
                                <Typography variant="body2" sx={{ mt: 0.5 }}>
                                    {viewCase.assignedTo
                                        ? `${viewCase.assignedTo.firstName || ""} ${viewCase.assignedTo.lastName || ""}`.trim() || viewCase.assignedTo.username
                                        : "Unassigned"}
                                </Typography>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                                <Typography variant="overline" color="text.secondary">Created By</Typography>
                                <Typography variant="body2" sx={{ mt: 0.5 }}>
                                    {viewCase.createdBy
                                        ? `${viewCase.createdBy.firstName || ""} ${viewCase.createdBy.lastName || ""}`.trim() || viewCase.createdBy.username
                                        : "—"}
                                </Typography>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                                <Typography variant="overline" color="text.secondary">Created</Typography>
                                <Typography variant="body2" sx={{ mt: 0.5 }}>
                                    {new Date(viewCase.createdAt).toLocaleString()}
                                </Typography>
                            </Grid>
                            <Grid item xs={12} sm={6}>
                                <Typography variant="overline" color="text.secondary">Last Updated</Typography>
                                <Typography variant="body2" sx={{ mt: 0.5 }}>
                                    {new Date(viewCase.updatedAt).toLocaleString()}
                                </Typography>
                            </Grid>
                            {viewCase.slaDeadline && (
                                <Grid item xs={12} sm={6}>
                                    <Typography variant="overline" color="text.secondary">SLA Deadline</Typography>
                                    <Typography
                                        variant="body2"
                                        sx={{
                                            mt: 0.5,
                                            color: new Date(viewCase.slaDeadline) < new Date() ? "var(--danger)" : "text.primary",
                                            fontWeight: new Date(viewCase.slaDeadline) < new Date() ? 600 : 400,
                                        }}
                                    >
                                        {new Date(viewCase.slaDeadline).toLocaleString()}
                                        {new Date(viewCase.slaDeadline) < new Date() && " (OVERDUE)"}
                                    </Typography>
                                </Grid>
                            )}
                            {viewCase.daysOpen !== undefined && (
                                <Grid item xs={12} sm={6}>
                                    <Typography variant="overline" color="text.secondary">Age</Typography>
                                    <Typography variant="body2" sx={{ mt: 0.5 }}>
                                        {viewCase.daysOpen === 0 ? "Today" : `${viewCase.daysOpen} day${viewCase.daysOpen !== 1 ? "s" : ""}`}
                                    </Typography>
                                </Grid>
                            )}
                        </Grid>
                    </DialogContent>
                )}
                <DialogActions>
                    {viewCase && <Button component={Link} to={`/records/CASE/${viewCase.id}`} onClick={() => setViewCase(null)} sx={{ color: "var(--gold)", textTransform: "none" }}>Trace record</Button>}
                    <Button onClick={() => setViewCase(null)} sx={{ textTransform: "none" }}>Close</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
