import { Component, type ErrorInfo, type ReactNode } from "react";
import { Box, Typography, Button } from "@mui/material";
import { Refresh as RefreshIcon } from "@mui/icons-material";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  isChunkError: boolean;
}

export class RouteErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, isChunkError: false };
  }

  static getDerivedStateFromError(error: Error): State {
    const isChunkError =
      error.message.includes("Failed to fetch dynamically imported module") ||
      error.message.includes("Loading chunk") ||
      error.message.includes("ChunkLoadError") ||
      error.name === "ChunkLoadError";
    return { hasError: true, isChunkError };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Route error boundary caught:", error, info);
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            height: "60vh",
            gap: 2,
            color: "text.secondary",
          }}
        >
          <Typography variant="h6" sx={{ color: "text.primary" }}>
            {this.state.isChunkError ? "Page failed to load" : "Something went wrong"}
          </Typography>
          <Typography variant="body2" sx={{ maxWidth: 400, textAlign: "center" }}>
            {this.state.isChunkError
              ? "A required file could not be loaded. This usually happens after a deployment. Please refresh the page."
              : "An unexpected error occurred on this page."}
          </Typography>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={this.handleReload}
            sx={{
              color: "#8B4049",
              borderColor: "#8B4049",
              textTransform: "none",
              "&:hover": { borderColor: "#6B3037", backgroundColor: "rgba(139,64,73,0.08)" },
            }}
          >
            Reload page
          </Button>
        </Box>
      );
    }
    return this.props.children;
  }
}
