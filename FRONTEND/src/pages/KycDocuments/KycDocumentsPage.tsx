import { useState, useRef } from "react";
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
    MenuItem,
    Snackbar,
    Stack,
} from "@mui/material";
import {
    Search as SearchIcon,
    Description as DocIcon,
    InsertDriveFile as FileIcon,
    OpenInNew as OpenIcon,
    CloudUpload as UploadIcon,
    CheckCircle as ApproveIcon,
    Cancel as RejectIcon,
} from "@mui/icons-material";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import { getApiUrl } from "../../config/api";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";

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

const DOCUMENT_TYPES: Array<{ value: string; label: string }> = [
    { value: "ID_DOCUMENT", label: "ID Document" },
    { value: "PROOF_OF_ADDRESS", label: "Proof of Address" },
    { value: "BUSINESS_REGISTRATION", label: "Business Registration" },
    { value: "BANK_STATEMENT", label: "Bank Statement" },
    { value: "PASSPORT", label: "Passport" },
    { value: "LICENSE", label: "License" },
    { value: "UTILITY_BILL", label: "Utility Bill" },
    { value: "OTHER", label: "Other" },
];

type Toast = { open: boolean; severity: "success" | "error" | "info"; message: string };

interface DocumentsDialogProps {
    merchantId: number;
    merchantName: string;
    open: boolean;
    onClose: () => void;
}

function DocumentsDialog({ merchantId, merchantName, open, onClose }: DocumentsDialogProps) {
    const queryClient = useQueryClient();
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [uploadType, setUploadType] = useState<string>("ID_DOCUMENT");
    const [uploading, setUploading] = useState(false);
    const [verifyingId, setVerifyingId] = useState<number | null>(null);
    const [toast, setToast] = useState<Toast>({ open: false, severity: "success", message: "" });

    const { data: documents, isLoading, isError } = useQuery<MerchantDocument[]>({
        queryKey: ["merchant-documents", merchantId],
        queryFn: () =>
            apiClient.get<MerchantDocument[]>(`merchants/${merchantId}/documents`).catch(() => []),
        enabled: open,
    });

    const closeUpload = () => {
        setUploadOpen(false);
        setUploadFile(null);
        setUploadType("ID_DOCUMENT");
        if (fileInputRef.current) fileInputRef.current.value = "";
    };

    const handleUpload = async () => {
        if (!uploadFile) {
            setToast({ open: true, severity: "error", message: "Please select a file to upload." });
            return;
        }
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", uploadFile);
            formData.append("type", uploadType);

            // apiClient hardcodes Content-Type: application/json which breaks
            // multipart boundary detection — issue the request directly.
            const pspId = sessionStorage.getItem("_psp") ?? "0";
            const response = await fetch(getApiUrl(`merchants/${merchantId}/documents`), {
                method: "POST",
                credentials: "include",
                headers: {
                    "X-PSP-ID": pspId,
                },
                body: formData,
            });
            if (!response.ok) {
                throw new Error(`Upload failed (HTTP ${response.status})`);
            }

            await queryClient.invalidateQueries({ queryKey: ["merchant-documents", merchantId] });
            setToast({ open: true, severity: "success", message: "Document uploaded successfully." });
            closeUpload();
        } catch (err) {
            const msg = err instanceof Error ? err.message : "Upload failed.";
            setToast({ open: true, severity: "error", message: msg });
        } finally {
            setUploading(false);
        }
    };

    const handleVerify = async (documentId: number, approved: boolean) => {
        setVerifyingId(documentId);
        try {
            await apiClient.put(`documents/${documentId}/verify?approved=${approved}`);
            await queryClient.invalidateQueries({ queryKey: ["merchant-documents", merchantId] });
            setToast({
                open: true,
                severity: "success",
                message: approved ? "Document approved." : "Document rejected.",
            });
        } catch (err: any) {
            const msg = err?.message || "Verification failed.";
            setToast({ open: true, severity: "error", message: msg });
        } finally {
            setVerifyingId(null);
        }
    };

    return (
        <>
            <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
                <DialogTitle sx={{ fontWeight: 600, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span>KYC Documents — {merchantName}</span>
                    <Button
                        size="small"
                        variant="contained"
                        startIcon={<UploadIcon />}
                        onClick={() => setUploadOpen(true)}
                        sx={{
                            backgroundColor: "#8B4049",
                            textTransform: "none",
                            "&:hover": { backgroundColor: "#6b313a" },
                        }}
                    >
                        Upload
                    </Button>
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
                                const url = doc.fileUrl || doc.url;
                                const status = (doc.status || "").toUpperCase();
                                const isUnverified = status === "PENDING" || status === "" || status === "UNDER_REVIEW";

                                return (
                                    <Box key={doc.id}>
                                        {idx > 0 && <Divider />}
                                        <ListItem
                                            secondaryAction={
                                                <Stack direction="row" spacing={0.5} alignItems="center">
                                                    {url && (
                                                        <Button
                                                            size="small"
                                                            endIcon={<OpenIcon />}
                                                            onClick={() => window.open(url, "_blank", "noopener,noreferrer")}
                                                            sx={{ color: "#8B4049", textTransform: "none" }}
                                                        >
                                                            Open
                                                        </Button>
                                                    )}
                                                    {isUnverified && (
                                                        <>
                                                            <Button
                                                                size="small"
                                                                color="success"
                                                                startIcon={<ApproveIcon sx={{ fontSize: 16 }} />}
                                                                disabled={verifyingId === doc.id}
                                                                onClick={() => handleVerify(doc.id, true)}
                                                                sx={{ textTransform: "none" }}
                                                            >
                                                                Approve
                                                            </Button>
                                                            <Button
                                                                size="small"
                                                                color="error"
                                                                startIcon={<RejectIcon sx={{ fontSize: 16 }} />}
                                                                disabled={verifyingId === doc.id}
                                                                onClick={() => handleVerify(doc.id, false)}
                                                                sx={{ textTransform: "none" }}
                                                            >
                                                                Reject
                                                            </Button>
                                                        </>
                                                    )}
                                                </Stack>
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

            <Dialog open={uploadOpen} onClose={uploading ? undefined : closeUpload} maxWidth="xs" fullWidth>
                <DialogTitle sx={{ fontWeight: 600 }}>Upload Document</DialogTitle>
                <DialogContent dividers>
                    <Stack spacing={2} sx={{ mt: 1 }}>
                        <TextField
                            select
                            label="Document Type"
                            value={uploadType}
                            onChange={(e) => setUploadType(e.target.value)}
                            size="small"
                            fullWidth
                        >
                            {DOCUMENT_TYPES.map((opt) => (
                                <MenuItem key={opt.value} value={opt.value}>
                                    {opt.label}
                                </MenuItem>
                            ))}
                        </TextField>

                        <Button
                            component="label"
                            variant="outlined"
                            startIcon={<UploadIcon />}
                            sx={{
                                textTransform: "none",
                                borderColor: "#8B4049",
                                color: "#8B4049",
                                "&:hover": { borderColor: "#6b313a", backgroundColor: "rgba(139,64,73,0.04)" },
                            }}
                        >
                            {uploadFile ? "Change file" : "Choose file"}
                            <input
                                ref={fileInputRef}
                                hidden
                                type="file"
                                onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)}
                            />
                        </Button>

                        {uploadFile && (
                            <Typography variant="caption" sx={{ color: "text.secondary" }}>
                                Selected: {uploadFile.name} ({Math.round(uploadFile.size / 1024)} KB)
                            </Typography>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={closeUpload} disabled={uploading}>
                        Cancel
                    </Button>
                    <Button
                        onClick={handleUpload}
                        disabled={uploading || !uploadFile}
                        variant="contained"
                        sx={{
                            backgroundColor: "#8B4049",
                            "&:hover": { backgroundColor: "#6b313a" },
                        }}
                    >
                        {uploading ? <CircularProgress size={18} sx={{ color: "white" }} /> : "Upload"}
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                open={toast.open}
                autoHideDuration={4000}
                onClose={() => setToast((t) => ({ ...t, open: false }))}
                anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
            >
                <Alert
                    severity={toast.severity}
                    onClose={() => setToast((t) => ({ ...t, open: false }))}
                    variant="filled"
                    sx={{ width: "100%" }}
                >
                    {toast.message}
                </Alert>
            </Snackbar>
        </>
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
        <HokekaPageShell title="KYC" subtitle="Manage merchant KYC status and compliance documents" noCard>
        <Box>
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
        </HokekaPageShell>
    );
}
