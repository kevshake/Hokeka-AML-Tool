/**
 * Report Preview Table Component
 * Displays report data preview with sorting and pagination
 */

import { useState } from "react";
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TablePagination,
  Typography,
  Chip,
  IconButton,
  Tooltip,
} from "@mui/material";
import {
  ArrowUpward as ArrowUpIcon,
  ArrowDownward as ArrowDownIcon,
  UnfoldMore as SortIcon,
} from "@mui/icons-material";

interface ReportPreviewTableProps {
  columns: string[];
  data: Record<string, unknown>[];
  totalRows: number;
  loading?: boolean;
}

type SortDirection = "asc" | "desc" | null;

interface SortState {
  column: string | null;
  direction: SortDirection;
}

export default function ReportPreviewTable({
  columns,
  data,
  totalRows,
  loading = false,
}: ReportPreviewTableProps) {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [sort, setSort] = useState<SortState>({ column: null, direction: null });

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleSort = (column: string) => {
    let direction: SortDirection = "asc";
    if (sort.column === column) {
      if (sort.direction === "asc") {
        direction = "desc";
      } else if (sort.direction === "desc") {
        direction = null;
      }
    }
    setSort({ column: direction ? column : null, direction });
  };

  const formatCellValue = (value: unknown): string => {
    if (value === null || value === undefined) return "-";
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") {
      // Format as currency if it looks like money
      if (Math.abs(value) > 100) {
        return new Intl.NumberFormat("en-US", {
          style: "currency",
          currency: "USD",
        }).format(value);
      }
      return value.toLocaleString();
    }
    if (value instanceof Date) {
      return value.toLocaleDateString();
    }
    return String(value);
  };

  const getSortIcon = (column: string) => {
    if (sort.column !== column) {
      return <SortIcon sx={{ fontSize: 16, opacity: 0.3 }} />;
    }
    return sort.direction === "asc" ? (
      <ArrowUpIcon sx={{ fontSize: 16, color: "#800020" }} />
    ) : (
      <ArrowDownIcon sx={{ fontSize: 16, color: "#800020" }} />
    );
  };

  // Sort data locally
  const sortedData = [...data];
  if (sort.column && sort.direction) {
    sortedData.sort((a, b) => {
      const aVal = a[sort.column!];
      const bVal = b[sort.column!];
      
      if (aVal === null || aVal === undefined) return 1;
      if (bVal === null || bVal === undefined) return -1;
      
      if (typeof aVal === "number" && typeof bVal === "number") {
        return sort.direction === "asc" ? aVal - bVal : bVal - aVal;
      }
      
      const aStr = String(aVal).toLowerCase();
      const bStr = String(bVal).toLowerCase();
      
      if (sort.direction === "asc") {
        return aStr.localeCompare(bStr);
      }
      return bStr.localeCompare(aStr);
    });
  }

  // Paginate data
  const paginatedData = sortedData.slice(
    page * rowsPerPage,
    page * rowsPerPage + rowsPerPage
  );

  if (loading) {
    return (
      <Box sx={{ p: 4, textAlign: "center" }}>
        <Typography color="text.secondary">Loading preview data...</Typography>
      </Box>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Box sx={{ p: 4, textAlign: "center" }}>
        <Typography color="text.secondary">No data available for preview</Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ mb: 2, display: "flex", alignItems: "center", gap: 1 }}>
        <Chip
          label={`${totalRows.toLocaleString()} total rows`}
          size="small"
          sx={{
            backgroundColor: "rgba(128, 0, 32, 0.1)",
            color: "#800020",
            fontWeight: 500,
          }}
        />
        <Chip
          label={`Showing ${Math.min(data.length, rowsPerPage)} sample rows`}
          size="small"
          variant="outlined"
        />
      </Box>

      <TableContainer
        component={Paper}
        sx={{
          borderRadius: "16px",
          boxShadow: "0 4px 20px rgba(0, 0, 0, 0.05)",
          maxHeight: 400,
        }}
      >
        <Table stickyHeader size="small">
          <TableHead>
            <TableRow>
              {columns.map((column) => (
                <TableCell
                  key={column}
                  sx={{
                    backgroundColor: "#fafafa",
                    fontWeight: 600,
                    color: "#2c3e50",
                    whiteSpace: "nowrap",
                    cursor: "pointer",
                    "&:hover": {
                      backgroundColor: "#f0f0f0",
                    },
                  }}
                  onClick={() => handleSort(column)}
                >
                  <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                    {column}
                    <Tooltip title="Sort" arrow>
                      <IconButton size="small" sx={{ p: 0.3 }}>
                        {getSortIcon(column)}
                      </IconButton>
                    </Tooltip>
                  </Box>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {paginatedData.map((row, index) => (
              <TableRow
                key={index}
                sx={{
                  "&:nth-of-type(odd)": {
                    backgroundColor: "rgba(128, 0, 32, 0.02)",
                  },
                  "&:hover": {
                    backgroundColor: "rgba(128, 0, 32, 0.05)",
                  },
                }}
              >
                {columns.map((column) => (
                  <TableCell
                    key={column}
                    sx={{
                      maxWidth: 200,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    <Tooltip title={formatCellValue(row[column])} arrow>
                      <span>{formatCellValue(row[column])}</span>
                    </Tooltip>
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        component="div"
        count={data.length}
        page={page}
        onPageChange={handleChangePage}
        rowsPerPage={rowsPerPage}
        onRowsPerPageChange={handleChangeRowsPerPage}
        rowsPerPageOptions={[5, 10, 25, 50]}
        sx={{
          mt: 2,
          "& .MuiTablePagination-select": {
            borderRadius: "8px",
          },
        }}
      />
    </Box>
  );
}
