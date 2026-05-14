import { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  Chip,
  Button,
  CircularProgress,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stack,
  Tooltip,
} from "@mui/material";
import {
  FolderOpen as OpenIcon,
  HourglassEmpty as InProgressIcon,
  CheckCircle as ClosedIcon,
  Warning as EscalatedIcon,
  NewReleases as NewIcon,
  Search as InvestigatingIcon,
  RateReview as PendingReviewIcon,
} from "@mui/icons-material";
import { useNavigate } from "react-router-dom";
import { useCases } from "../../features/api/queries";
import { useAllPsps } from "../../features/api/queries";
import { useAuth } from "../../contexts/AuthContext";
import type { Case, CaseStatus } from "../../types";

// ─── Status display config ───────────────────────────────────────────────────

interface StatusConfig {
  color: string;
  bgColor: string;
  borderColor: string;
  label: string;
  Icon: React.ComponentType<{ sx?: object }>;
}

const STATUS_CONFIG: Record<CaseStatus, StatusConfig> = {
  NEW: {
    color: "#2980b9",
    bgColor: "#ebf5fb",
    borderColor: "#aed6f1",
    label: "New",
    Icon: NewIcon,
  },
  ASSIGNED: {
    color: "#8e6b3e",
    bgColor: "#fef9e7",
    borderColor: "#f9e79f",
    label: "Assigned",
    Icon: OpenIcon,
  },
  INVESTIGATING: {
    color: "#8e44ad",
    bgColor: "#f5eef8",
    borderColor: "#d2b4de",
    label: "Investigating",
    Icon: InvestigatingIcon,
  },
  PENDING_REVIEW: {
    color: "#e67e22",
    bgColor: "#fef5e7",
    borderColor: "#fad7a0",
    label: "Pending Review",
    Icon: PendingReviewIcon,
  },
  RESOLVED: {
    color: "#27ae60",
    bgColor: "#e9f7ef",
    borderColor: "#a9dfbf",
    label: "Resolved",
    Icon: ClosedIcon,
  },
  ESCALATED: {
    color: "#c0392b",
    bgColor: "#fdedec",
    borderColor: "#f1948a",
    label: "Escalated",
    Icon: EscalatedIcon,
  },
};

const FALLBACK_STATUS: StatusConfig = {
  color: "#7f8c8d",
  bgColor: "#f4f6f7",
  borderColor: "#d5d8dc",
  label: "Unknown",
  Icon: InProgressIcon,
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function daysOpenLabel(createdAt: string, daysOpen?: number): string {
  if (daysOpen !== undefined) return `${daysOpen}d open`;
  const ms = Date.now() - new Date(createdAt).getTime();
  const d = Math.floor(ms / 86_400_000);
  return d === 0 ? "today" : `${d}d open`;
}

// ─── Single timeline event card ───────────────────────────────────────────────

function TimelineEvent({ caseItem }: { caseItem: Case }) {
  const navigate = useNavigate();
  const cfg = STATUS_CONFIG[caseItem.status] ?? FALLBACK_STATUS;
  const { Icon } = cfg;

  return (
    <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
      {/* Vertical line + icon */}
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          flexShrink: 0,
          width: 40,
        }}
      >
        <Box
          sx={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            backgroundColor: cfg.bgColor,
            border: `2px solid ${cfg.borderColor}`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: cfg.color,
            flexShrink: 0,
          }}
        >
          <Icon sx={{ fontSize: 18 }} />
        </Box>
        {/* connector line — rendered in parent */}
      </Box>

      {/* Event card */}
      <Paper
        sx={{
          flex: 1,
          p: 2,
          mb: 0,
          border: `1px solid ${cfg.borderColor}`,
          backgroundColor: cfg.bgColor,
          borderRadius: 2,
        }}
      >
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            flexWrap: "wrap",
            gap: 1,
          }}
        >
          <Box>
            <Typography
              variant="subtitle2"
              sx={{ fontWeight: 700, color: "text.primary", mb: 0.5 }}
            >
              {caseItem.caseReference}
            </Typography>
            <Typography
              variant="body2"
              sx={{ color: "text.secondary", maxWidth: 520 }}
            >
              {caseItem.description || "No description provided."}
            </Typography>
          </Box>

          <Stack direction="row" spacing={1} alignItems="center" flexShrink={0}>
            <Chip
              label={cfg.label}
              size="small"
              sx={{
                backgroundColor: cfg.bgColor,
                color: cfg.color,
                border: `1px solid ${cfg.borderColor}`,
                fontWeight: 600,
                fontSize: "0.7rem",
              }}
            />
            <Tooltip title="Open case detail">
              <Button
                size="small"
                variant="outlined"
                onClick={() => navigate(`/cases/all`)}
                sx={{
                  fontSize: "0.7rem",
                  py: 0.25,
                  px: 1,
                  borderColor: cfg.borderColor,
                  color: cfg.color,
                  "&:hover": { borderColor: cfg.color, backgroundColor: "transparent" },
                }}
              >
                View Case
              </Button>
            </Tooltip>
          </Stack>
        </Box>

        <Box
          sx={{
            display: "flex",
            gap: 2,
            mt: 1,
            flexWrap: "wrap",
          }}
        >
          <Typography variant="caption" sx={{ color: "text.disabled" }}>
            Updated: {formatTimestamp(caseItem.updatedAt)}
          </Typography>
          <Typography variant="caption" sx={{ color: "text.disabled" }}>
            Created: {formatTimestamp(caseItem.createdAt)}
          </Typography>
          <Typography variant="caption" sx={{ color: "text.disabled" }}>
            {daysOpenLabel(caseItem.createdAt, caseItem.daysOpen)}
          </Typography>
          {caseItem.assignedTo && (
            <Typography variant="caption" sx={{ color: "text.disabled" }}>
              Assigned: {caseItem.assignedTo.firstName ?? caseItem.assignedTo.username}
            </Typography>
          )}
        </Box>
      </Paper>
    </Box>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function CasesTimeline() {
  const { user } = useAuth();
  const isSuperAdmin = (user?.pspId ?? 0) === 0;

  const [statusFilter, setStatusFilter] = useState<string>("");
  const [pspFilter, setPspFilter] = useState<string>("");

  const { data: allPsps } = useAllPsps();

  // Fetch up to 50 most-recent cases; apply status filter server-side
  const { data, isLoading, isError } = useCases({
    page: 0,
    size: 50,
    status: statusFilter || undefined,
  });

  const cases: Case[] = (data?.content ?? []).slice().sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  );

  // Client-side PSP filter (cases don't carry pspId directly but super-admin can filter by assignee)
  // We surface the PSP dropdown only for super-admins; filter is best-effort based on assignedTo.psp
  const filtered = pspFilter
    ? cases.filter((c) => String((c.assignedTo as (typeof c.assignedTo & { pspId?: number }) | undefined)?.pspId ?? "") === pspFilter)
    : cases;

  return (
    <Box>
      {/* Filter bar */}
      <Box sx={{ display: "flex", gap: 2, mb: 3, flexWrap: "wrap" }}>
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel>Status</InputLabel>
          <Select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            label="Status"
          >
            <MenuItem value="">All Statuses</MenuItem>
            {(Object.keys(STATUS_CONFIG) as CaseStatus[]).map((s) => (
              <MenuItem key={s} value={s}>
                {STATUS_CONFIG[s].label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {isSuperAdmin && allPsps && allPsps.length > 0 && (
          <FormControl size="small" sx={{ minWidth: 200 }}>
            <InputLabel>PSP</InputLabel>
            <Select
              value={pspFilter}
              onChange={(e) => setPspFilter(e.target.value)}
              label="PSP"
            >
              <MenuItem value="">All PSPs</MenuItem>
              {allPsps.map((psp) => (
                <MenuItem key={psp.id} value={String(psp.id)}>
                  {psp.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}

        <Typography
          variant="caption"
          sx={{ color: "text.disabled", alignSelf: "center" }}
        >
          Showing {filtered.length} most-recent case event
          {filtered.length !== 1 ? "s" : ""}
        </Typography>
      </Box>

      {/* Content */}
      {isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress size={32} sx={{ color: "#8B4049" }} />
        </Box>
      )}

      {isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load case timeline. Please try again.
        </Alert>
      )}

      {!isLoading && !isError && filtered.length === 0 && (
        <Paper
          sx={{
            p: 6,
            textAlign: "center",
            border: "1px dashed rgba(0,0,0,0.15)",
            backgroundColor: "background.paper",
            borderRadius: 2,
          }}
        >
          <Typography variant="body1" sx={{ color: "text.secondary" }}>
            No case events match the selected filters.
          </Typography>
        </Paper>
      )}

      {!isLoading && !isError && filtered.length > 0 && (
        <Box sx={{ position: "relative" }}>
          {/* Vertical connector line behind all cards */}
          <Box
            sx={{
              position: "absolute",
              left: 19,
              top: 18,
              bottom: 18,
              width: 2,
              backgroundColor: "divider",
              zIndex: 0,
            }}
          />

          <Stack spacing={2} sx={{ position: "relative", zIndex: 1 }}>
            {filtered.map((c) => (
              <TimelineEvent key={c.id} caseItem={c} />
            ))}
          </Stack>
        </Box>
      )}
    </Box>
  );
}
