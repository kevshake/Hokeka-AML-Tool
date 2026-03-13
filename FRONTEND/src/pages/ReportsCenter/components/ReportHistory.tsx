/**
 * Report History Component
 * Displays generated report history with status and actions
 */

import { useState, type ReactNode } from "react";
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
  draft: { bg: "rgba(158, 158, 158, 0.1)", text: "#757575" },
  scheduled: { bg: "rgba(25, 118, 210, 0.1)", text: "#1976D2" },
  generating: { bg: "rgba(255, 152, 0, 0.1)", text: "#F57C00" },
  completed: { bg: "rgba(46, 125, 50, 0.1)", text: "#2E7D32" },
  failed: { bg: "rgba(211, 47, 47, 0.1)", text: "#C62828" },
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
          <Typography variant="h6" sx={{ fontWeight: 600, color: "#2c3e50" }}>
            Report History
          </Typography>
          {!loading && (
            <Chip
              label={`${instances.length} reports`}
              size="small"
              sx={{
                backgroundColor: "rgba(128, 0, 32, 0.1)",
                color: "#800020",
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
            color: "#800020",
            "&: hover": {
              backgroundColor: "rgba(128, 0, 32, 0.05)",
            },
          }}
        >
          Refresh
        </Button>
      </Box>

      <TableContainer
        component={Paper}
        sx={{
          borderRadius: "16px",
          boxShadow: "0 4px 20px rgba(0, 0, 0, 0.05)",
          overflow: "hidden",
        }}
      >
        <Table size="small">
          <TableHead>
            <TableRow sx={{ backgroundColor: "#fafafa" }}>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Report</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Status</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Format</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Created</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Size</TableCell>
              <TableCell sx={{ fontWeight: 600, color: "#2c3e50" }}>Created By</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600, color: "#2c3e50" }}>
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
                      "&: hover": {
                        backgroundColor: "rgba(128, 0, 32, 0.02)",
                      },
                    }}
                  >
                    <TableCell>
                      <Box>
                        <Typography
                          variant="body2"
                          sx={{ fontWeight: 500, color: "#2c3e50" }}
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
                        {instance.status === "completed" && instance.fileUrl && (
                          <Tooltip title="Download" arrow>
                            <span>
                              <IconButton
                                size="small"
                                onClick={() => handleDownload(instance)}
                                disabled={isDownloading || isDeleting}
                                sx={{
                                  color: "#800020",
                                  "&: hover": {
                                    backgroundColor: "rgba(128, 0, 32, 0.1)",
                                  },
                                }}
                              >
                                {isDownloading ? (
                                  <CircularProgress size={16} sx={{ color: "#800020" }} />
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
                                "&: hover": {
                                  color: "#d32f2f",
                                  backgroundColor: "rgba(211, 47, 47, 0.1)",
                                },
                              }}
                            >
                              {isDeleting ? (
                                <CircularProgress size={16} sx={{ color: "#d32f2f" }} />
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
                borderRadius: "8px",
              },
              "& .Mui-selected": {
                backgroundColor: "#800020 !important",
                color: "#fff !important",
              },
            }}
          />
        </Box>
      )}
    </Box>
  );
}
