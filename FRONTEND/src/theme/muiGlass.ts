import type { SxProps, Theme } from "@mui/material";

/** Shared glass panel styling for MUI Paper/TableContainer overrides. */
export const glassPanelSx: SxProps<Theme> = {
  bgcolor: "var(--glass-surface)",
  backgroundImage: "none",
  border: "1px solid var(--glass-border)",
  borderRadius: "var(--radius)",
  boxShadow: "var(--glass-shadow)",
  backdropFilter: "var(--glass-blur)",
  WebkitBackdropFilter: "var(--glass-blur)",
};

export const glassTableContainerSx: SxProps<Theme> = {
  ...glassPanelSx,
  overflow: "hidden",
};
