/**
 * Report History Component
 * Displays generated report history with status and actions
 */

import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
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
  IconButton,
  Tooltip,
  Typography,
  Button,
  Pagination,
  Skeleton,
  CircularProgress,
} from "@mui/material";
import {
  Download as DownloadIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  PictureAsPdf as PdfIcon,
  TableChart as CsvIcon,
  Assessment as ExcelIcon,
  Schedule as ScheduledIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
  HourglassEmpty as PendingIcon,
  AccountTree as TraceIcon,
} from "@mui/icons-material";
import type {
  ReportInstance,
  ReportStatus,
  ExportFormat,
} from "../../../types/reports/reportDefinitions";
import { formatFileSize } from "../../../features/api/reportQueries";

interface ReportHistoryProps {
  instances: ReportInstance[];
  loading?: boolean;
  onDownload: (instance: ReportInstance, format: ExportFormat) => void;
  onDelete: (instanceId: string) => void;
  onRefresh: () => void;
  emptyState?: ReactNode;
}

const STATUS_ICONS: Record<ReportStatus, typeof SuccessIcon> = {
  draft: PendingIcon,
  scheduled: ScheduledIcon,
  generating: PendingIcon,
  completed: SuccessIcon,
  failed: ErrorIcon,
};

const STATUS_COLORS: Record<ReportStatus, { bg: string; text: string }> = {
  draft: { bg: "rgba(158, 158, 158, 0.1)", text: "var(--muted)" },
  scheduled: { bg: "var(--info-soft)", text: "var(--info)" },
  generating: { bg: "rgba(255, 152, 0, 0.1)", text: "var(--warning)" },
  completed: { bg: "var(--success-soft)", text: "var(--success)" },
  failed: { bg: "var(--danger-soft)", text: "var(--danger)" },
};

const FORMAT_ICONS: Record<ExportFormat, typeof PdfIcon> = {
  PDF: PdfIcon,
  CSV: CsvIcon,
  Excel: ExcelIcon,
};

// Loading skeleton row
const SkeletonRow = () => (
  <TableRow>
    {[...Array(7)].map((_, i) => (
      <TableCell key={i}>
        <Skeleton variant="text" width={i === 0 ? 150 : i === 3 ? 120 : 80} />
      </TableCell>
    ))}
  </TableRow>
);

export default function ReportHistory({
  instances,
  loading = false,
  onDownload,
  onDelete,
  onRefresh,
  emptyState,
}: ReportHistoryProps) {
  const [page, setPage] = useState(1);
  const [rowsPerPage] = useState(10);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const handleDownload = async (instance: ReportInstance) => {
    setDownloadingId(instance.id);
    try {
      await onDownload(instance, instance.format);
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDelete = async (instanceId: string) => {
    setDeletingId(instanceId);
    try {
      await onDelete(instanceId);
    } finally {
      setDeletingId(null);
    }
  };

  const paginatedInstances = instances.slice(
    (page - 1) * rowsPerPage,
    page * rowsPerPage
  );

  const totalPages = Math.ceil(instances.length / rowsPerPage);

  return (
    <Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 2,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Typography variant="h6" sx={{ fontWeight: 600, color: "var(--ink)" }}>
            Report History
          </Typography>
          {!loading && (
            <Chip
              label={`${instances.length} reports`}
              size="small"
              sx={{
                backgroundColor: "var(--brand-soft)",
                color: "var(--gold)",
              }}
            />
          )}
        </Box>

        <Button
          startIcon={
            loading ? (
              <CircularProgress size={16} sx={{ color: "inherit" }} />
            ) : (
              <RefreshIcon />
            )
          }
          onClick={onRefresh}
          disabled={loading}
          sx={{
            color: "var(--gold)",
            "&:hover": {
              backgroundColor: "var(--surface-2)",
            },
          }}
        >
          Refresh
        </Button>
      </Box>

      <TableContainer
        component={Paper}
        sx={{
          borderRadius: "var(--radius)",
          backgroundColor: "var(--surface-2)",
          border: "1px solid var(--line)",
          boxShadow: "none",
          overflow: "hidden",
        }}
      >
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Report</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Format</TableCell>
              <TableCell>Created</TableCell>
              <TableCell>Size</TableCell>
              <TableCell>Created By</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, color: "var(--ink)" }}>
                Actions
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <>
                <SkeletonRow />
                <SkeletonRow />
                <SkeletonRow />
                <SkeletonRow />
                <SkeletonRow />
              </>
            ) : paginatedInstances.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 4 }}>
                  {emptyState || (
                    <Typography color="text.secondary">No reports generated yet</Typography>
                  )}
                </TableCell>
              </TableRow>
            ) : (
              paginatedInstances.map((instance) => {
                const StatusIcon = STATUS_ICONS[instance.status];
                const statusColors = STATUS_COLORS[instance.status];
                const FormatIcon = FORMAT_ICONS[instance.format];
                const isDeleting = deletingId === instance.id;
                const isDownloading = downloadingId === instance.id;

                return (
                  <TableRow
                    key={instance.id}
                    sx={{
                      "&:hover": {
                        backgroundColor: "var(--surface-2)",
                      },
                    }}
                  >
                    <TableCell>
                      <Box>
                        <Typography
                          variant="body2"
                          sx={{ fontWeight: 500, color: "var(--ink)" }}
                        >
                          {instance.reportName}
                        </Typography>
                        {instance.errorMessage && (
                          <Typography
                            variant="caption"
                            color="error"
                            sx={{ display: "block", mt: 0.5 }}
                          >
                            {instance.errorMessage}
                          </Typography>
                        )}
                      </Box>
                    </TableCell>

                    <TableCell>
                      <Chip
                        size="small"
                        icon={
                          isDeleting ? (
                            <CircularProgress size={14} sx={{ color: statusColors.text }} />
                          ) : (
                            <StatusIcon sx={{ fontSize: 16 }} />
                          )
                        }
                        label={isDeleting ? "Deleting..." : instance.status}
                        sx={{
                          backgroundColor: statusColors.bg,
                          color: statusColors.text,
                          fontWeight: 500,
                          textTransform: "capitalize",
                          "& .MuiChip-icon": {
                            color: statusColors.text,
                          },
                        }}
                      />
                    </TableCell>

                    <TableCell>
                      <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                        <FormatIcon sx={{ fontSize: 16, color: "text.secondary" }} />
                        <Typography variant="body2">{instance.format}</Typography>
                      </Box>
                    </TableCell>

                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {formatDate(instance.createdAt)}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {formatFileSize(instance.fileSize)}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {instance.createdBy}
                      </Typography>
                    </TableCell>

                    <TableCell align="right">
                      <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 0.5 }}>
                        <Tooltip title="View record trail" arrow>
                          <IconButton component={Link} to={`/records/REPORT_EXECUTION/${instance.id}`} size="small" sx={{ color: "var(--gold)", "&:hover": { backgroundColor: "var(--brand-soft)" } }}>
                            <TraceIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        {instance.status === "completed" && instance.fileUrl && (
                          <Tooltip title="Download" arrow>
                            <span>
                              <IconButton
                                size="small"
                                onClick={() => handleDownload(instance)}
                                disabled={isDownloading || isDeleting}
                                sx={{
                                  color: "var(--gold)",
                                  "&:hover": {
                                    backgroundColor: "var(--brand-soft)",
                                  },
                                }}
                              >
                                {isDownloading ? (
                                  <CircularProgress size={16} sx={{ color: "var(--gold)" }} />
                                ) : (
                                  <DownloadIcon fontSize="small" />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
                        )}
                        <Tooltip title="Delete" arrow>
                          <span>
                            <IconButton
                              size="small"
                              onClick={() => handleDelete(instance.id)}
                              disabled={isDeleting || isDownloading}
                              sx={{
                                color: "text.secondary",
                                "&:hover": {
                                  color: "var(--danger)",
                                  backgroundColor: "var(--danger-soft)",
                                },
                              }}
                            >
                              {isDeleting ? (
                                <CircularProgress size={16} sx={{ color: "var(--danger)" }} />
                              ) : (
                                <DeleteIcon fontSize="small" />
                              )}
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

      {totalPages > 1 && (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
          <Pagination
            count={totalPages}
            page={page}
            onChange={(_, value) => setPage(value)}
            color="primary"
            sx={{
              "& .MuiPaginationItem-root": {
                borderRadius: "var(--radius)",
              },
              "& .Mui-selected": {
                backgroundColor: "var(--brand-soft) !important",
                color: "var(--gold) !important",
                border: "1px solid color-mix(in srgb, var(--gold) 35%, transparent)",
              },
            }}
          />
        </Box>
      )}
    </Box>
  );
}
