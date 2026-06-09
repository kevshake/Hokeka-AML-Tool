import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  Chip,
  CircularProgress,
} from "@mui/material";
import { useChargebackDisputes } from "../../../features/api/queries";

export default function ChargebackDisputesPanel() {
  const { data: disputes = [], isLoading } = useChargebackDisputes();

  return (
    <Paper sx={{ p: 2, mt: 3, borderRadius: "12px" }}>
      <Typography variant="h6" gutterBottom>
        Verifi RDR / Chargeback Disputes
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Live disputes ingested from Visa/Verifi RDR webhooks. Configure webhooks at{" "}
        <code>POST /api/v1/integrations/verifi/rdr</code>.
      </Typography>

      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      ) : disputes.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No chargeback disputes recorded yet.
        </Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Amount</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>ARN</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {disputes.slice(0, 20).map((d: any) => (
              <TableRow key={d.id}>
                <TableCell>{d.caseDate || d.createdAt?.slice?.(0, 10) || "—"}</TableCell>
                <TableCell>{d.notificationType}</TableCell>
                <TableCell>
                  {d.rdrStatus ? (
                    <Chip
                      size="small"
                      label={d.rdrStatus}
                      color={d.rdrStatus === "accepted" ? "success" : "default"}
                    />
                  ) : (
                    "—"
                  )}
                </TableCell>
                <TableCell>
                  {d.caseAmount != null
                    ? `${d.caseAmount} ${d.caseCurrency || ""}`.trim()
                    : "—"}
                </TableCell>
                <TableCell>{d.reasonCode || "—"}</TableCell>
                <TableCell sx={{ fontFamily: "monospace", fontSize: "0.75rem" }}>
                  {d.acquirerReferenceNumber || "—"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Paper>
  );
}
