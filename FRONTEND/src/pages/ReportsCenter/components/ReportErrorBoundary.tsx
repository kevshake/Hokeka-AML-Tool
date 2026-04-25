/**
 * Report Error Boundary
 * Catches errors in report components and displays fallback UI
 */

import { Component, type ReactNode } from "react";
import { Box, Paper, Typography, Button, Alert } from "@mui/material";
import { ErrorOutline as ErrorIcon, Refresh as RefreshIcon } from "@mui/icons-material";
import type { ReportApiError } from "../../../features/api/reportQueries";

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
  onReset?: () => void;
  reportName?: string;
}

interface State {
  hasError: boolean;
  error: Error | ReportApiError | null;
  errorInfo: React.ErrorInfo | null;
}

export default class ReportErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error, errorInfo: null };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    this.setState({ error, errorInfo });
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    this.props.onReset?.();
  };

  render() {
    if (this.state.hasError) {
      // Custom fallback UI
      if (this.props.fallback) {
        return this.props.fallback;
      }

      const isApiError = this.state.error && "statusCode" in this.state.error;
      const statusCode = isApiError ? (this.state.error as ReportApiError).statusCode : null;
      const errorCode = isApiError ? (this.state.error as ReportApiError).errorCode : null;
      const details = isApiError ? (this.state.error as ReportApiError).details : null;

      return (
        <Paper
          elevation={0}
          sx={{
            p: 4,
            borderRadius: "16px",
            backgroundColor: "rgba(211, 47, 47, 0.05)",
            border: "1px solid rgba(211, 47, 47, 0.2)",
            textAlign: "center",
          }}
        >
          <Box
            sx={{
              width: 64,
              height: 64,
              borderRadius: "50%",
              backgroundColor: "rgba(211, 47, 47, 0.1)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              mx: "auto",
              mb: 2,
            }}
          >
            <ErrorIcon sx={{ fontSize: 32, color: "#d32f2f" }} />
          </Box>

          <Typography variant="h6" sx={{ fontWeight: 600, color: "#d32f2f", mb: 1 }}>
            {statusCode === 503
              ? "Report Service Unavailable"
              : statusCode === 504
              ? "Report Generation Timeout"
              : statusCode === 429
              ? "Too Many Requests"
              : this.props.reportName
              ? `Failed to Load "${this.props.reportName}"`
              : "Report Failed to Load"}
          </Typography>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {this.state.error?.message || "An unexpected error occurred while processing the report."}
          </Typography>

          {errorCode && (
            <Alert severity="error" sx={{ mb: 2, textAlign: "left" }}>
              Error Code: {errorCode}
            </Alert>
          )}

          {details && details.length > 0 && (
            <Box sx={{ textAlign: "left", mb: 2 }}>
              {details.map((detail: string, index: number) => (
                <Typography key={index} variant="caption" display="block" color="text.secondary">
                  • {detail}
                </Typography>
              ))}
            </Box>
          )}

          {statusCode && statusCode >= 500 && (
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 2 }}>
              This appears to be a server error. Please try again later or contact support if the problem
              persists.
            </Typography>
          )}

          <Button
            variant="contained"
            startIcon={<RefreshIcon />}
            onClick={this.handleReset}
            sx={{
              backgroundColor: "#800020",
              "&:hover": { backgroundColor: "#600018" },
              borderRadius: "10px",
            }}
          >
            Try Again
          </Button>
        </Paper>
      );
    }

    return this.props.children;
  }
}
