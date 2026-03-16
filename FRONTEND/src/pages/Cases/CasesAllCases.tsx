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
    Chip,
    Button,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Tooltip,
    TablePagination,
    CircularProgress,
} from "@mui/material";
import { useCases } from "../../features/api/queries";
import type { CaseStatus, Priority } from "../../types";

const statusColors: Record<CaseStatus, string> = {
    NEW: "#3498db",
    ASSIGNED: "#f39c12",
    INVESTIGATING: "#9b59b6",
    PENDING_REVIEW: "#e67e22",
    RESOLVED: "#2ecc71",
    ESCALATED: "#e74c3c",
};

const priorityColors: Record<Priority, string> = {
    CRITICAL: "#e74c3c",
    HIGH: "#e67e22",
    MEDIUM: "#f39c12",
    LOW: "#95a5a6",
};

export default function CasesAllCases() {
    const [statusFilter, setStatusFilter] = useState<string>("");
    const [page, setPage] = useState({ index: 0, size: 25 });
    
    const { data: cases, isLoading, isError, error } = useCases({
        page: page.index,
        size: page.size,
        status: statusFilter || undefined,
    });

    return (
        <Box>
            <Box sx={{ display: "flex", justifyContent: "flex-end", alignItems: "center", mb: 2 }}>
                <Box sx={{ display: "flex", gap: 2 }}>
                    <Tooltip title="Filter the cases list by their current workflow status. Options include: New (recently created), Assigned (assigned to an analyst), Investigating (under active review), Pending Review (awaiting supervisor approval), Resolved (completed), and Escalated (requires higher-level attention). Select 'All' to show cases in all statuses." arrow placement="top" enterDelay={2000}>
                        <FormControl size="small" sx={{ minWidth: 200 }}>
                            <InputLabel sx={{ color: "text.secondary" }}>Filter by Status</InputLabel>
                            <Select
                                value={statusFilter}
                                onChange={(e) => setStatusFilter(e.target.value)}
                                label="Filter by Status"
                                sx={{
                                    color: "text.primary",
                                    "& .MuiOutlinedInput-notchedOutline": {
                                        borderColor: "rgba(0,0,0,0.3)",
                                    },
                                }}
                            >
                                <MenuItem value="">All</MenuItem>
                                <MenuItem value="NEW">New</MenuItem>
                                <MenuItem value="ASSIGNED">Assigned</MenuItem>
                                <MenuItem value="INVESTIGATING">Investigating</MenuItem>
                                <MenuItem value="PENDING_REVIEW">Pending Review</MenuItem>
                                <MenuItem value="RESOLVED">Resolved</MenuItem>
                                <MenuItem value="ESCALATED">Escalated</MenuItem>
                            </Select>
                        </FormControl>
                    </Tooltip>
                    <Tooltip title="Create a new compliance case for investigation. This opens a case creation form where you can specify the case type (sanctions match, suspicious activity, etc.), assign it to an analyst, set priority level, link related transactions and alerts, and add initial notes. The case will be tracked through its complete investigation workflow." arrow placement="top" enterDelay={2000}>
                        <Button variant="contained" sx={{ backgroundColor: "#8B4049", "&:hover": { backgroundColor: "#6B3037" } }}>
                            Create Case
                        </Button>
                    </Tooltip>
                </Box>
            </Box>

            <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell sx={{ color: "text.secondary" }}>Reference</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Status</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Priority</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Description</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Assigned To</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Created</TableCell>
                            <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 2 }}>
                                    <CircularProgress size={24} />
                                </TableCell>
                            </TableRow>
                        ) : isError ? (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "#e74c3c", py: 2 }}>
                                    Error loading cases: {error instanceof Error ? error.message : "Unknown error"}
                                </TableCell>
                            </TableRow>
                        ) : cases?.content && cases.content.length > 0 ? (
                            cases.content.map((caseItem) => (
                                <TableRow key={caseItem.id} hover>
                                    <TableCell sx={{ color: "text.primary" }}>{caseItem.caseReference}</TableCell>
                                    <TableCell>
                                        <Chip
                                            label={caseItem.status}
                                            size="small"
                                            sx={{
                                                backgroundColor: statusColors[caseItem.status] + "20",
                                                color: statusColors[caseItem.status],
                                                border: `1px solid ${statusColors[caseItem.status]}`,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            label={caseItem.priority}
                                            size="small"
                                            sx={{
                                                backgroundColor: priorityColors[caseItem.priority] + "20",
                                                color: priorityColors[caseItem.priority],
                                                border: `1px solid ${priorityColors[caseItem.priority]}`,
                                            }}
                                        />
                                    </TableCell>
                                    <TableCell sx={{ color: "text.primary", maxWidth: 300 }}>
                                        {caseItem.description}
                                    </TableCell>
                                    <TableCell sx={{ color: "text.primary" }}>
                                        {caseItem.assignedTo?.username || "Unassigned"}
                                    </TableCell>
                                    <TableCell sx={{ color: "text.secondary" }}>
                                        {new Date(caseItem.createdAt).toLocaleDateString()}
                                    </TableCell>
                                    <TableCell>
                                        <Tooltip title="View the complete case details including case timeline, all related transactions and alerts, investigation notes, assigned analyst information, priority and status history, evidence attachments, and case resolution details. Opens the full case management interface." arrow enterDelay={2000}>
                                            <Button size="small" sx={{ color: "#8B4049" }}>
                                                View
                                            </Button>
                                        </Tooltip>
                                    </TableCell>
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={7} align="center" sx={{ color: "text.disabled", py: 2 }}>
                                    No cases found
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
                />
            </TableContainer>
        </Box>
    );
}

