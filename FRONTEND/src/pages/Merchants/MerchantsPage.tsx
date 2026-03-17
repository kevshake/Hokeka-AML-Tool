import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
  Button,
  TablePagination,
  CircularProgress,
  Tooltip,
} from "@mui/material";
import { useState } from "react";
import { useMerchants } from "../../features/api/queries";

const riskColors: Record<string, string> = {
  LOW: "#2ecc71",
  MEDIUM: "#f39c12",
  HIGH: "#e74c3c",
};

export default function MerchantsPage() {
  const [page, setPage] = useState({ index: 0, size: 25 });
  
  const { data: merchants, isLoading } = useMerchants({
    page: page.index,
    size: page.size,
  });

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography variant="h6" sx={{ color: "text.primary", fontWeight: 600 }}>
          Merchants
        </Typography>
        <Tooltip title="Create and onboard a new merchant to the system. This opens a form where you can enter merchant business details, KYC information, risk assessment data, and configure transaction limits. The merchant will go through the onboarding workflow after submission." arrow enterDelay={2000}>
          <Button variant="contained" sx={{ backgroundColor: "#a93226", "&:hover": { backgroundColor: "#922b21" } }}>
            Add Merchant
          </Button>
        </Tooltip>
      </Box>

      <TableContainer component={Paper} sx={{ backgroundColor: "background.paper", border: "1px solid rgba(0,0,0,0.1)" }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell sx={{ color: "text.secondary" }}>Merchant ID</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Business Name</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>MCC</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Risk Level</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>KRS</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>CRA</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>KYC Status</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Contract Status</TableCell>
              <TableCell sx={{ color: "text.secondary" }}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            ) : merchants?.content && merchants.content.length > 0 ? (
              merchants.content.map((merchant) => (
                <TableRow key={merchant.id} hover>
                  <TableCell sx={{ color: "text.primary" }}>{merchant.merchantId}</TableCell>
                  <TableCell sx={{ color: "text.primary" }}>{merchant.businessName}</TableCell>
                  <TableCell sx={{ color: "text.primary" }}>{merchant.mcc || "N/A"}</TableCell>
                  <TableCell>
                    {merchant.riskLevel && (
                      <Chip
                        label={merchant.riskLevel}
                        size="small"
                        sx={{
                          backgroundColor: riskColors[merchant.riskLevel] + "20",
                          color: riskColors[merchant.riskLevel],
                          border: `1px solid ${riskColors[merchant.riskLevel]}`,
                        }}
                      />
                    )}
                  </TableCell>
                  <TableCell sx={{ color: "text.primary" }}>{merchant.krs?.toFixed(1) || "N/A"}</TableCell>
                  <TableCell sx={{ color: "text.primary" }}>{merchant.cra?.toFixed(1) || "N/A"}</TableCell>
                  <TableCell sx={{ color: "text.primary" }}>
                    {merchant.kycStatus || "N/A"}
                  </TableCell>
                  <TableCell sx={{ color: "text.primary" }}>
                    {merchant.contractStatus || "N/A"}
                  </TableCell>
                  <TableCell>
                    <Tooltip title="View complete merchant profile including business information, KYC status, risk scores (KRS and CRA), transaction history, compliance status, contract details, and all associated alerts and cases. Opens a detailed merchant dashboard." arrow enterDelay={2000}>
                      <Button size="small" sx={{ color: "#a93226" }}>
                        View
                      </Button>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ color: "text.disabled", py: 2 }}>
                  No merchants found
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          rowsPerPageOptions={[10, 25, 50, 100]}
          component="div"
          count={merchants?.totalElements || 0}
          rowsPerPage={page.size}
          page={page.index}
          onPageChange={(_, newPage) => setPage(prev => ({ ...prev, index: newPage }))}
          onRowsPerPageChange={(e) => setPage({ index: 0, size: parseInt(e.target.value, 10) })}
        />
      </TableContainer>
    </Box>
  );
}

