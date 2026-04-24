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
    Chip,
    CircularProgress,
    Alert,
    TextField,
    InputAdornment,
} from "@mui/material";
import { Search as SearchIcon, Description as DocIcon } from "@mui/icons-material";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";

interface MerchantDocument {
    id: number;
    merchantId: number;
    merchantName: string;
    documentType: string;
    fileName: string;
    status: string;
    expiryDate?: string;
    uploadedAt: string;
}

interface MerchantsPage {
    content: Array<{ id: number; businessName: string; kycStatus: string; riskLevel: string }>;
    totalElements: number;
}

const kycStatusConfig: Record<string, { color: string; bgColor: string; label: string }> = {
    APPROVED: { color: "#27ae60", bgColor: "#e9f7ef", label: "Approved" },
    PENDING: { color: "#e67e22", bgColor: "#fef5e7", label: "Pending" },
    REJECTED: { color: "#c0392b", bgColor: "#fdedec", label: "Rejected" },
    UNDER_REVIEW: { color: "#8e44ad", bgColor: "#f5eef8", label: "Under Review" },
    EXPIRED: { color: "#7f8c8d", bgColor: "#f4f6f7", label: "Expired" },
};

export default function KycDocumentsPage() {
    const [search, setSearch] = useState("");

    const { data: merchantsPage, isLoading, isError } = useQuery<MerchantsPage>({
        queryKey: ["merchants-kyc"],
        queryFn: () => apiClient.get<MerchantsPage>("merchants?page=0&size=50"),
    });

    const merchants = merchantsPage?.content || [];
    const filtered = merchants.filter((m) =>
        m.businessName.toLowerCase().includes(search.toLowerCase())
    );

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
                    onChange={(e) => setSearch(e.target.value)}
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
                        ) : filtered.length > 0 ? (
                            filtered.map((merchant) => {
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
                                            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic" }}>
                                                View documents →
                                            </Typography>
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
            </TableContainer>
        </Box>
    );
}
