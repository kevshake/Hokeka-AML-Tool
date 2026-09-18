import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
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
  Button,
  Drawer,
  Divider,
  Stack,
} from "@mui/material";
import { OpenInNew as CaseIcon } from "@mui/icons-material";
import HokekaPageShell from "../../components/Layout/HokekaPageShell";
import { useChargebackDisputes } from "../../features/api/queries";
import { apiClient } from "../../lib/apiClient";
import { useQuery } from "@tanstack/react-query";

interface ChargebackDispute {
  id: number;
  notificationType?: string;
  rdrStatus?: string;
  caseAmount?: number;
  caseCurrency?: string;
  reasonCode?: string;
  reasonCategory?: string;
  acquirerReferenceNumber?: string;
  caseDate?: string;
  createdAt?: string;
  merchantId?: number;
  pspTransactionId?: string;
  complianceCaseId?: number;
  alertId?: number;
  caseId?: string;
}

function useChargebackDispute(id: number | null) {
  return useQuery({
    queryKey: ["chargeback", "dispute", id],
    enabled: id != null,
    queryFn: async () => apiClient.get<ChargebackDispute>(`chargeback/disputes/${id}`),
  });
}

export default function ChargebacksPage() {
  const navigate = useNavigate();
  const { data: disputes = [], isLoading } = useChargebackDisputes();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { data: detail, isLoading: detailLoading } = useChargebackDispute(selectedId);

  const sorted = useMemo(
    () => [...disputes].sort((a, b) => (b.id ?? 0) - (a.id ?? 0)),
    [disputes]
  );

  return (
    <HokekaPageShell title="Chargeback Operations" subtitle="Verifi RDR disputes and case linkage">
      <Paper sx={{ p: 2, borderRadius: "12px" }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Disputes ingested from Visa/Verifi RDR webhooks. Open a row for detail and jump to linked compliance cases.
        </Typography>

        {isLoading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
            <CircularProgress />
          </Box>
        ) : sorted.length === 0 ? (
          <Typography color="text.secondary">No chargeback disputes recorded yet.</Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Date</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Amount</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Case</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sorted.map((d) => (
                <TableRow
                  key={d.id}
                  hover
                  sx={{ cursor: "pointer" }}
                  onClick={() => setSelectedId(d.id)}
                >
                  <TableCell>{d.id}</TableCell>
                  <TableCell>{d.caseDate || d.createdAt?.slice?.(0, 10) || "—"}</TableCell>
                  <TableCell>{d.notificationType || "—"}</TableCell>
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
                  <TableCell>{d.reasonCode || d.reasonCategory || "—"}</TableCell>
                  <TableCell>
                    {d.complianceCaseId ? (
                      <Button
                        size="small"
                        startIcon={<CaseIcon />}
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/cases/all?caseId=${d.complianceCaseId}`);
                        }}
                      >
                        #{d.complianceCaseId}
                      </Button>
                    ) : (
                      "—"
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Drawer
        anchor="right"
        open={selectedId != null}
        onClose={() => setSelectedId(null)}
        PaperProps={{ sx: { width: { xs: "100%", sm: 420 }, p: 2 } }}
      >
        {detailLoading || !detail ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <Stack spacing={1.5}>
            <Typography variant="h6">Dispute #{detail.id}</Typography>
            <Divider />
            <Button variant="outlined" startIcon={<CaseIcon />} onClick={() => navigate(`/records/CHARGEBACK_DISPUTE/${detail.id}`)}>
              Trace dispute record
            </Button>
            <DetailRow label="Verifi case" value={detail.caseId} />
            <DetailRow label="Notification" value={detail.notificationType} />
            <DetailRow label="RDR status" value={detail.rdrStatus} />
            <DetailRow
              label="Amount"
              value={
                detail.caseAmount != null
                  ? `${detail.caseAmount} ${detail.caseCurrency || ""}`.trim()
                  : undefined
              }
            />
            <DetailRow label="Reason" value={detail.reasonCode || detail.reasonCategory} />
            <DetailRow label="ARN" value={detail.acquirerReferenceNumber} mono />
            <DetailRow label="PSP txn" value={detail.pspTransactionId} mono />
            {detail.merchantId && <Button size="small" onClick={() => navigate(`/records/MERCHANT/${detail.merchantId}`)}>Merchant #{detail.merchantId}</Button>}
            {detail.alertId && <Button size="small" onClick={() => navigate(`/records/ALERT/${detail.alertId}`)}>Alert #{detail.alertId}</Button>}
            {detail.complianceCaseId && (
              <Button variant="contained" startIcon={<CaseIcon />} onClick={() => navigate(`/records/CASE/${detail.complianceCaseId}`)}>
                Trace compliance case #{detail.complianceCaseId}
              </Button>
            )}
          </Stack>
        )}
      </Drawer>
    </HokekaPageShell>
  );
}

function DetailRow({
  label,
  value,
  mono,
}: {
  label: string;
  value?: string | null;
  mono?: boolean;
}) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography sx={{ fontFamily: mono ? "monospace" : undefined, fontSize: mono ? "0.85rem" : undefined }}>
        {value || "—"}
      </Typography>
    </Box>
  );
}
