import { useState } from "react";
import {
    Box,
    Typography,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TablePagination,
    Chip,
    CircularProgress,
    Alert,
    TextField,
    InputAdornment,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    List,
    ListItem,
    ListItemText,
    ListItemIcon,
    Divider,
} from "@mui/material";
import {
    Search as SearchIcon,
    Description as DocIcon,
    InsertDriveFile as FileIcon,
    OpenInNew as OpenIcon,
} from "@mui/icons-material";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

interface MerchantsPage {
    content: Array<{ id: number; businessName: string; kycStatus: string; riskLevel: string }>;
    totalElements: number;
}

interface MerchantDocument {
    id: number;
    fileName?: string;
    name?: string;
    documentType?: string;
    type?: string;
    status?: string;
    uploadedAt?: string;
    createdAt?: string;
    url?: string;
    fileUrl?: string;
}

const kycStatusConfig: Record<string, { color: string; bgColor: string; label: string }> = {
    APPROVED: { color: "#27ae60", bgColor: "#e9f7ef", label: "Approved" },
    PENDING: { color: "#e67e22", bgColor: "#fef5e7", label: "Pending" },
    REJECTED: { color: "#c0392b", bgColor: "#fdedec", label: "Rejected" },
    UNDER_REVIEW: { color: "#8e44ad", bgColor: "#f5eef8", label: "Under Review" },
    EXPIRED: { color: "#7f8c8d", bgColor: "#f4f6f7", label: "Expired" },
};

interface DocumentsDialogProps {
    merchantId: number;
    merchantName: string;
    open: boolean;
    onClose: () => void;
}

function DocumentsDialog({ merchantId, merchantName, open, onClose }: DocumentsDialogProps) {
    const { data: documents, isLoading, isError } = useQuery<MerchantDocument[]>({
        queryKey: ["merchant-documents", merchantId],
        queryFn: () =>
            apiClient.get<MerchantDocument[]>(`merchants/${merchantId}/documents`).catch(() => []),
        enabled: open,
    });

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ fontWeight: 600 }}>
                KYC Documents — {merchantName}
            </DialogTitle>
            <DialogContent dividers sx={{ p: 0 }}>
                {isLoading ? (
                    <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
                        <CircularProgress size={28} sx={{ color: "#8B4049" }} />
                    </Box>
                ) : isError ? (
                    <Box sx={{ p: 3 }}>
                        <Alert severity="error">Failed to load documents.</Alert>
                    </Box>
                ) : documents && documents.length > 0 ? (
                    <List disablePadding>
                        {documents.map((doc, idx) => {
                            const name = doc.fileName || doc.name || `Document ${doc.id}`;
                            const type = doc.documentType || doc.type || "Document";
                            const date = doc.uploadedAt || doc.createdAt;
                            const url = doc.url || doc.fileUrl;
                            return (
                                <Box key={doc.id}>
                                    {idx > 0 && <Divider />}
                                    <ListItem
                                        secondaryAction={
                                            url ? (
                                                <Button
                                                    size="small"
                                                    endIcon={<OpenIcon />}
                                                    href={url}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    sx={{ color: "#8B4049" }}
                                                >
                                                    Open
                                                </Button>
                                            ) : undefined
                                        }
                                    >
                                        <ListItemIcon sx={{ minWidth: 36 }}>
                                            <FileIcon sx={{ color: "#8B4049", opacity: 0.8 }} />
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={name}
                                            secondary={
                                                <Box component="span" sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                                                    <span>{type}</span>
                                                    {date && (
                                                        <span style={{ color: "#999" }}>
                                                            · {new Date(date).toLocaleDateString()}
                                                        </span>
                                                    )}
                                                    {doc.status && (
                                                        <Chip
                                                            label={doc.status}
                                                            size="small"
                                                            sx={{ height: 18, fontSize: "0.65rem" }}
                                                        />
                                                    )}
                                                </Box>
                                            }
                                        />
                                    </ListItem>
                                </Box>
                            );
                        })}
                    </List>
                ) : (
                    <Box sx={{ py: 5, textAlign: "center", color: "text.disabled" }}>
                        <FileIcon sx={{ fontSize: 40, mb: 1, opacity: 0.4 }} />
                        <Typography variant="body2">No documents on file for this merchant.</Typography>
                    </Box>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Close</Button>
            </DialogActions>
        </Dialog>
    );
}

export default function KycDocumentsPage() {
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(25);
    const [selectedMerchant, setSelectedMerchant] = useState<{ id: number; name: string } | null>(null);

    const queryString = [
        `page=${page}`,
        `size=${rowsPerPage}`,
        search ? `search=${encodeURIComponent(search)}` : "",
    ].filter(Boolean).join("&");

    const { data: merchantsPage, isLoading, isError } = useQuery<MerchantsPage>({
        queryKey: ["merchants-kyc", page, rowsPerPage, search],
        queryFn: () => apiClient.get<MerchantsPage>(`merchants?${queryString}`),
        placeholderData: (prev) => prev,
    });

    const merchants = merchantsPage?.content || [];
    const totalElements = merchantsPage?.totalElements || 0;

    return (
        <Box>
            <Typography variant="h6" sx={{ color: "text.primary", mb: 0.5, fontWeight: 600 }}>
                KYC / Documents
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Manage merchant KYC status and compliance documents.
            </Typography>

            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
                <TextField
                    size="small"
                    placeholder="Search merchants..."
                    value={search}
                    onChange={(e) => { setSearch(e.target.value); setPage(0); }}
                    sx={{ width: 280 }}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon fontSize="small" sx={{ color: "text.secondary" }} />
                            </InputAdornment>
                        ),
                    }}
                />
            </Box>

            {isError && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    Failed to load KYC data.
                </Alert>
            )}

            <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.08)", borderRadius: 2 }}>
                <Table size="small">
                    <TableHead>
                        <TableRow sx={{ backgroundColor: "rgba(0,0,0,0.02)" }}>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Merchant</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>KYC Status</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Risk Level</TableCell>
                            <TableCell sx={{ color: "text.secondary", fontWeight: 600 }}>Documents</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={4} align="center" sx={{ py: 6 }}>
                                    <CircularProgress size={32} sx={{ color: "#8B4049" }} />
                                </TableCell>
                            </TableRow>
                        ) : merchants.length > 0 ? (
                            merchants.map((merchant) => {
                                const kycCfg = kycStatusConfig[merchant.kycStatus] || { color: "#666", bgColor: "#f5f5f5", label: merchant.kycStatus };
                                return (
                                    <TableRow key={merchant.id} hover sx={{ "&:hover": { backgroundColor: "rgba(139,64,73,0.04)" } }}>
                                        <TableCell>
                                            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                                                <DocIcon sx={{ fontSize: 18, color: "#8B4049", opacity: 0.7 }} />
                                                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                                    {merchant.businessName}
                                                </Typography>
                                            </Box>
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={kycCfg.label}
                                                size="small"
                                                sx={{
                                                    backgroundColor: kycCfg.bgColor,
                                                    color: kycCfg.color,
                                                    fontWeight: 500,
                                                    fontSize: "0.75rem",
                                                    borderRadius: 1,
                                                    height: 24,
                                                }}
                                            />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" color="text.secondary">
                                                {merchant.riskLevel}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Button
                                                size="small"
                                                variant="text"
                                                startIcon={<FileIcon sx={{ fontSize: 16 }} />}
                                                onClick={() =>
                                                    setSelectedMerchant({
                                                        id: merchant.id,
                                                        name: merchant.businessName,
                                                    })
                                                }
                                                sx={{
                                                    color: "#8B4049",
                                                    textTransform: "none",
                                                    fontSize: "0.8rem",
                                                    "&:hover": { backgroundColor: "rgba(139,64,73,0.08)" },
                                                }}
                                            >
                                                View documents
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                );
                            })
                        ) : (
                            <TableRow>
                                <TableCell colSpan={4} align="center" sx={{ py: 8, color: "text.disabled" }}>
                                    <Typography variant="body1">No merchants found</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
                <TablePagination
                    rowsPerPageOptions={[10, 25, 50]}
                    component="div"
                    count={totalElements}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onPageChange={(_, newPage) => setPage(newPage)}
                    onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
                />
            </TableContainer>

            {selectedMerchant && (
                <DocumentsDialog
                    merchantId={selectedMerchant.id}
                    merchantName={selectedMerchant.name}
                    open={true}
                    onClose={() => setSelectedMerchant(null)}
                />
            )}
        </Box>
    );
}
