import { useState, useRef, useCallback, useEffect } from "react";
import {
  Box,
  Paper,
  Typography,
  CircularProgress,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stack,
  Divider,
  IconButton,
  Tooltip,
  Alert as MuiAlert,
  Button,
} from "@mui/material";
import {
  ZoomIn as ZoomInIcon,
  ZoomOut as ZoomOutIcon,
  CenterFocusStrong as ResetIcon,
  Close as CloseIcon,
  AccountTree as CaseIcon,
  Receipt as TxnIcon,
  Person as UserIcon,
  Assignment as SarIcon,
  Store as MerchantIcon,
  HelpOutline as UnknownIcon,
} from "@mui/icons-material";
import { useCases, useCaseNetwork } from "../../features/api/queries";
import type { Case, CaseStatus } from "../../types";

// ─── Types ────────────────────────────────────────────────────────────────────

interface NetworkNode {
  id: string;
  type: string;
  label: string;
}

interface NetworkEdge {
  from: string;
  to: string;
  type: string;
  label: string;
}

interface NetworkGraphDTO {
  nodes: NetworkNode[];
  edges: NetworkEdge[];
}

// ─── Constants ────────────────────────────────────────────────────────────────

const NODE_RADIUS = 28;

const NODE_COLORS: Record<string, { fill: string; stroke: string; text: string }> = {
  CASE:        { fill: "#f0e6ff", stroke: "#8B4049", text: "#8B4049" },
  TRANSACTION: { fill: "#e8f4fd", stroke: "#2980b9", text: "#2980b9" },
  SAR:         { fill: "#fef9e7", stroke: "#d68910", text: "#d68910" },
  USER:        { fill: "#e9f7ef", stroke: "#27ae60", text: "#27ae60" },
  MERCHANT:    { fill: "#fdedec", stroke: "#c0392b", text: "#c0392b" },
};

const EDGE_COLORS: Record<string, string> = {
  RELATED_CASE:    "#8B4049",
  HAS_TRANSACTION: "#2980b9",
  HAS_SAR:         "#d68910",
  ASSIGNED_TO:     "#27ae60",
  HAS_MERCHANT:    "#c0392b",
  RELATED_ENTITY:  "#7f8c8d",
};

const STATUS_CONFIG: Record<string, { color: string; bgColor: string; label: string }> = {
  NEW:            { color: "#3498db", bgColor: "#ebf5fb",  label: "New" },
  ASSIGNED:       { color: "#8e6b3e", bgColor: "#fef9e7",  label: "Assigned" },
  INVESTIGATING:  { color: "#8e44ad", bgColor: "#f5eef8",  label: "Investigating" },
  PENDING_REVIEW: { color: "#c0392b", bgColor: "#fdedec",  label: "Pending Review" },
  RESOLVED:       { color: "#27ae60", bgColor: "#e9f7ef",  label: "Resolved" },
  ESCALATED:      { color: "#922b21", bgColor: "#fdedec",  label: "Escalated" },
};

// ─── Force-layout simulation (no library needed) ─────────────────────────────

interface SimNode extends NetworkNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
}

function runForceLayout(
  nodes: NetworkNode[],
  edges: NetworkEdge[],
  width: number,
  height: number,
): SimNode[] {
  const cx = width / 2;
  const cy = height / 2;

  // Initial positions on a circle to avoid overlap
  const simNodes: SimNode[] = nodes.map((n, i) => {
    const angle = (i / nodes.length) * Math.PI * 2;
    const r = Math.min(width, height) * 0.32;
    return { ...n, x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle), vx: 0, vy: 0 };
  });

  const nodeById = new Map<string, SimNode>();
  simNodes.forEach((n) => nodeById.set(n.id, n));

  // Run iterations
  const ITERATIONS = 300;
  const IDEAL_EDGE_LEN = 130;
  const REPULSION = 4000;
  const ATTRACTION = 0.04;
  const CENTER_GRAVITY = 0.01;
  const DAMPING = 0.88;

  for (let iter = 0; iter < ITERATIONS; iter++) {
    // Repulsion between all node pairs
    for (let i = 0; i < simNodes.length; i++) {
      for (let j = i + 1; j < simNodes.length; j++) {
        const a = simNodes[i];
        const b = simNodes[j];
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 0.1;
        const force = REPULSION / (dist * dist);
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        a.vx -= fx;
        a.vy -= fy;
        b.vx += fx;
        b.vy += fy;
      }
    }

    // Attraction along edges
    edges.forEach((e) => {
      const a = nodeById.get(e.from);
      const b = nodeById.get(e.to);
      if (!a || !b) return;
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const dist = Math.sqrt(dx * dx + dy * dy) || 0.1;
      const force = ATTRACTION * (dist - IDEAL_EDGE_LEN);
      const fx = (dx / dist) * force;
      const fy = (dy / dist) * force;
      a.vx += fx;
      a.vy += fy;
      b.vx -= fx;
      b.vy -= fy;
    });

    // Gravity toward center
    simNodes.forEach((n) => {
      n.vx += (cx - n.x) * CENTER_GRAVITY;
      n.vy += (cy - n.y) * CENTER_GRAVITY;
    });

    // Integrate + damp
    simNodes.forEach((n) => {
      n.vx *= DAMPING;
      n.vy *= DAMPING;
      n.x += n.vx;
      n.y += n.vy;
      // Keep inside bounds with padding
      n.x = Math.max(NODE_RADIUS + 10, Math.min(width - NODE_RADIUS - 10, n.x));
      n.y = Math.max(NODE_RADIUS + 10, Math.min(height - NODE_RADIUS - 10, n.y));
    });
  }

  return simNodes;
}

// ─── Node icon helper ─────────────────────────────────────────────────────────

function nodeIcon(type: string) {
  switch (type) {
    case "CASE":        return <CaseIcon sx={{ fontSize: 14 }} />;
    case "TRANSACTION": return <TxnIcon sx={{ fontSize: 14 }} />;
    case "SAR":         return <SarIcon sx={{ fontSize: 14 }} />;
    case "USER":        return <UserIcon sx={{ fontSize: 14 }} />;
    case "MERCHANT":    return <MerchantIcon sx={{ fontSize: 14 }} />;
    default:            return <UnknownIcon sx={{ fontSize: 14 }} />;
  }
}

// ─── SVG Graph Canvas ─────────────────────────────────────────────────────────

interface GraphCanvasProps {
  nodes: SimNode[];
  edges: NetworkEdge[];
  selectedId: string | null;
  onSelectNode: (id: string | null) => void;
}

function GraphCanvas({ nodes, edges, selectedId, onSelectNode }: GraphCanvasProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragStart = useRef({ mouseX: 0, mouseY: 0, panX: 0, panY: 0 });

  const nodeById = new Map<string, SimNode>();
  nodes.forEach((n) => nodeById.set(n.id, n));

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const delta = -e.deltaY * 0.001;
    setZoom((z) => Math.min(3, Math.max(0.25, z + delta * z)));
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    if ((e.target as Element).closest(".graph-node")) return;
    setDragging(true);
    dragStart.current = { mouseX: e.clientX, mouseY: e.clientY, panX: pan.x, panY: pan.y };
  }, [pan]);

  const handleMouseMove = useCallback((e: React.MouseEvent<SVGSVGElement>) => {
    if (!dragging) return;
    setPan({
      x: dragStart.current.panX + (e.clientX - dragStart.current.mouseX),
      y: dragStart.current.panY + (e.clientY - dragStart.current.mouseY),
    });
  }, [dragging]);

  const handleMouseUp = useCallback(() => setDragging(false), []);

  const handleZoomIn  = () => setZoom((z) => Math.min(3, z * 1.25));
  const handleZoomOut = () => setZoom((z) => Math.max(0.25, z / 1.25));
  const handleReset   = () => { setZoom(1); setPan({ x: 0, y: 0 }); };

  // Build unique arrow marker ids per edge type
  const edgeTypes = [...new Set(edges.map((e) => e.type))];

  return (
    <Box sx={{ position: "relative", width: "100%", height: "100%" }}>
      {/* Zoom controls */}
      <Stack
        direction="column"
        spacing={0.5}
        sx={{ position: "absolute", top: 12, right: 12, zIndex: 10 }}
      >
        <Tooltip title="Zoom in" placement="left">
          <IconButton size="small" onClick={handleZoomIn} sx={{ backgroundColor: "background.paper", boxShadow: 1, border: "1px solid", borderColor: "divider" }}>
            <ZoomInIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title="Zoom out" placement="left">
          <IconButton size="small" onClick={handleZoomOut} sx={{ backgroundColor: "background.paper", boxShadow: 1, border: "1px solid", borderColor: "divider" }}>
            <ZoomOutIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title="Reset view" placement="left">
          <IconButton size="small" onClick={handleReset} sx={{ backgroundColor: "background.paper", boxShadow: 1, border: "1px solid", borderColor: "divider" }}>
            <ResetIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>

      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        style={{ cursor: dragging ? "grabbing" : "grab", display: "block" }}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <defs>
          {edgeTypes.map((type) => (
            <marker
              key={type}
              id={`arrow-${type}`}
              markerWidth="8"
              markerHeight="8"
              refX="7"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
            >
              <path
                d="M0,0 L0,6 L8,3 z"
                fill={EDGE_COLORS[type] ?? "#aaa"}
              />
            </marker>
          ))}
        </defs>

        <g transform={`translate(${pan.x},${pan.y}) scale(${zoom})`}>
          {/* Edges */}
          {edges.map((edge, i) => {
            const src = nodeById.get(edge.from);
            const tgt = nodeById.get(edge.to);
            if (!src || !tgt) return null;
            const dx = tgt.x - src.x;
            const dy = tgt.y - src.y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            // Shorten line to node boundary
            const ux = dx / dist;
            const uy = dy / dist;
            const x1 = src.x + ux * (NODE_RADIUS + 2);
            const y1 = src.y + uy * (NODE_RADIUS + 2);
            const x2 = tgt.x - ux * (NODE_RADIUS + 4);
            const y2 = tgt.y - uy * (NODE_RADIUS + 4);
            const color = EDGE_COLORS[edge.type] ?? "#aaa";
            const midX = (x1 + x2) / 2;
            const midY = (y1 + y2) / 2;
            return (
              <g key={i}>
                <line
                  x1={x1} y1={y1} x2={x2} y2={y2}
                  stroke={color}
                  strokeWidth={1.5}
                  strokeOpacity={0.55}
                  markerEnd={`url(#arrow-${edge.type})`}
                />
                {/* Edge label */}
                <text
                  x={midX}
                  y={midY - 4}
                  textAnchor="middle"
                  fontSize={8}
                  fill={color}
                  fillOpacity={0.8}
                  style={{ pointerEvents: "none", userSelect: "none" }}
                >
                  {edge.label}
                </text>
              </g>
            );
          })}

          {/* Nodes */}
          {nodes.map((node) => {
            const colors = NODE_COLORS[node.type] ?? { fill: "#f5f5f5", stroke: "#aaa", text: "#555" };
            const isSelected = node.id === selectedId;
            // Truncate label to ~12 chars for display inside circle
            const displayLabel = node.label.length > 12 ? node.label.slice(0, 10) + "…" : node.label;
            return (
              <g
                key={node.id}
                className="graph-node"
                transform={`translate(${node.x},${node.y})`}
                onClick={(e) => { e.stopPropagation(); onSelectNode(isSelected ? null : node.id); }}
                style={{ cursor: "pointer" }}
              >
                <circle
                  r={NODE_RADIUS}
                  fill={colors.fill}
                  stroke={isSelected ? "#8B4049" : colors.stroke}
                  strokeWidth={isSelected ? 3 : 1.5}
                  filter={isSelected ? "drop-shadow(0 2px 6px rgba(139,64,73,0.4))" : undefined}
                />
                <text
                  textAnchor="middle"
                  dominantBaseline="central"
                  y={-7}
                  fontSize={9.5}
                  fontWeight={600}
                  fill={colors.text}
                  style={{ pointerEvents: "none", userSelect: "none" }}
                >
                  {displayLabel}
                </text>
                {/* Type badge at bottom */}
                <rect
                  x={-20}
                  y={12}
                  width={40}
                  height={13}
                  rx={4}
                  fill={colors.stroke}
                  fillOpacity={0.15}
                />
                <text
                  textAnchor="middle"
                  y={21}
                  fontSize={7.5}
                  fontWeight={700}
                  fill={colors.stroke}
                  style={{ pointerEvents: "none", userSelect: "none" }}
                >
                  {node.type}
                </text>
              </g>
            );
          })}
        </g>
      </svg>
    </Box>
  );
}

// ─── Detail Panel ─────────────────────────────────────────────────────────────

interface DetailPanelProps {
  node: SimNode | null;
  edges: NetworkEdge[];
  allNodes: SimNode[];
  onClose: () => void;
}

function DetailPanel({ node, edges, allNodes, onClose }: DetailPanelProps) {
  if (!node) return null;
  const colors = NODE_COLORS[node.type] ?? { fill: "#f5f5f5", stroke: "#aaa", text: "#555" };
  const nodeById = new Map(allNodes.map((n) => [n.id, n]));
  const connectedEdges = edges.filter((e) => e.from === node.id || e.to === node.id);

  return (
    <Paper
      elevation={4}
      sx={{
        position: "absolute",
        top: 12,
        left: 12,
        width: 260,
        zIndex: 20,
        borderRadius: 2,
        overflow: "hidden",
        border: "1px solid",
        borderColor: "divider",
      }}
    >
      {/* Header */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, px: 2, py: 1.5, backgroundColor: colors.fill, borderBottom: "1px solid", borderColor: "divider" }}>
        <Box sx={{ color: colors.text }}>
          {nodeIcon(node.type)}
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography variant="caption" sx={{ color: colors.text, fontWeight: 700, display: "block" }}>
            {node.type}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 600, color: "text.primary", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
            {node.label}
          </Typography>
        </Box>
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* Connections */}
      <Box sx={{ px: 2, py: 1.5 }}>
        <Typography variant="overline" color="text.secondary" sx={{ fontSize: "0.65rem" }}>
          Connections ({connectedEdges.length})
        </Typography>
        {connectedEdges.length === 0 ? (
          <Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>No connections</Typography>
        ) : (
          <Stack spacing={0.75} sx={{ mt: 0.75 }}>
            {connectedEdges.slice(0, 8).map((e, i) => {
              const otherId = e.from === node.id ? e.to : e.from;
              const other = nodeById.get(otherId);
              const isOut = e.from === node.id;
              const edgeColor = EDGE_COLORS[e.type] ?? "#aaa";
              return (
                <Box key={i} sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
                  <Box
                    sx={{
                      width: 6,
                      height: 6,
                      borderRadius: "50%",
                      backgroundColor: edgeColor,
                      flexShrink: 0,
                    }}
                  />
                  <Typography variant="caption" sx={{ color: "text.secondary", fontSize: "0.7rem" }}>
                    {isOut ? "→" : "←"} {e.label}
                  </Typography>
                  <Typography variant="caption" sx={{ color: "text.primary", fontWeight: 500, fontSize: "0.7rem", ml: "auto", maxWidth: 80, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {other?.label ?? otherId}
                  </Typography>
                </Box>
              );
            })}
            {connectedEdges.length > 8 && (
              <Typography variant="caption" color="text.disabled" sx={{ fontSize: "0.65rem" }}>
                +{connectedEdges.length - 8} more
              </Typography>
            )}
          </Stack>
        )}
      </Box>
    </Paper>
  );
}

// ─── Legend ───────────────────────────────────────────────────────────────────

function Legend() {
  const entries = Object.entries(NODE_COLORS);
  return (
    <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", alignItems: "center" }}>
      {entries.map(([type, colors]) => (
        <Box key={type} sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
          <Box sx={{ width: 10, height: 10, borderRadius: "50%", backgroundColor: colors.fill, border: `2px solid ${colors.stroke}` }} />
          <Typography variant="caption" sx={{ color: colors.text, fontWeight: 600, fontSize: "0.7rem" }}>
            {type}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

// ─── Per-case graph inner component ──────────────────────────────────────────

interface CaseGraphInnerProps {
  caseId: number;
}

const CANVAS_W = 900;
const CANVAS_H = 520;

function CaseGraphInner({ caseId }: CaseGraphInnerProps) {
  const { data, isLoading, isError } = useCaseNetwork(caseId);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [simNodes, setSimNodes] = useState<SimNode[]>([]);

  const graphData = data as NetworkGraphDTO | undefined;

  useEffect(() => {
    if (!graphData?.nodes?.length) {
      setSimNodes([]);
      setSelectedId(null);
      return;
    }
    const laid = runForceLayout(graphData.nodes, graphData.edges ?? [], CANVAS_W, CANVAS_H);
    setSimNodes(laid);
    setSelectedId(null);
  }, [graphData]);

  if (isLoading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: CANVAS_H }}>
        <CircularProgress size={32} sx={{ color: "#8B4049" }} />
      </Box>
    );
  }

  if (isError) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: CANVAS_H }}>
        <MuiAlert severity="warning" sx={{ maxWidth: 400 }}>
          Could not load network graph for this case. The case may have no linked transactions or the network endpoint is unavailable.
        </MuiAlert>
      </Box>
    );
  }

  if (!graphData?.nodes?.length) {
    return (
      <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: CANVAS_H, gap: 1 }}>
        <CaseIcon sx={{ fontSize: 40, color: "text.disabled" }} />
        <Typography variant="body2" color="text.disabled">
          No network data for this case yet.
        </Typography>
      </Box>
    );
  }

  const selectedNode = simNodes.find((n) => n.id === selectedId) ?? null;

  return (
    <Box sx={{ position: "relative", width: "100%", height: CANVAS_H }}>
      <GraphCanvas
        nodes={simNodes}
        edges={graphData.edges ?? []}
        selectedId={selectedId}
        onSelectNode={setSelectedId}
      />
      <DetailPanel
        node={selectedNode}
        edges={graphData.edges ?? []}
        allNodes={simNodes}
        onClose={() => setSelectedId(null)}
      />
    </Box>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function CasesNetworkGraph() {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [selectedCase, setSelectedCase] = useState<Case | null>(null);
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 20;

  const { data: casesPage, isLoading, isError } = useCases({
    page,
    size: PAGE_SIZE,
    status: (statusFilter as CaseStatus) || undefined,
  });

  const cases = casesPage?.content ?? [];

  // Auto-select first case when data arrives
  useEffect(() => {
    if (!selectedCase && cases.length > 0) {
      setSelectedCase(cases[0]);
    }
  }, [cases, selectedCase]);

  // Reset selected case when filter changes
  const handleStatusChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setSelectedCase(null);
    setPage(0);
  };

  const totalCases = casesPage?.totalElements ?? 0;
  const totalPages = casesPage?.totalPages ?? 0;

  return (
    <Box sx={{ mt: 1 }}>
      {/* Toolbar */}
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          mb: 2,
          pb: 2,
          borderBottom: "1px solid",
          borderColor: "divider",
          flexWrap: "wrap",
          gap: 1,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Typography variant="body2" color="text.secondary">
            {totalCases} cases
          </Typography>
          <Legend />
        </Box>

        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel sx={{ color: "text.secondary" }}>Filter by Status</InputLabel>
          <Select
            value={statusFilter}
            onChange={(e) => handleStatusChange(e.target.value)}
            label="Filter by Status"
            sx={{
              color: "text.primary",
              "& .MuiOutlinedInput-notchedOutline": { borderColor: "rgba(0,0,0,0.15)" },
              "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: "rgba(0,0,0,0.3)" },
            }}
          >
            <MenuItem value="">All Statuses</MenuItem>
            <MenuItem value="NEW">New</MenuItem>
            <MenuItem value="ASSIGNED">Assigned</MenuItem>
            <MenuItem value="INVESTIGATING">Investigating</MenuItem>
            <MenuItem value="PENDING_REVIEW">Pending Review</MenuItem>
            <MenuItem value="RESOLVED">Resolved</MenuItem>
            <MenuItem value="ESCALATED">Escalated</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {/* Main layout: case list (left) + graph canvas (right) */}
      <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
        {/* Case selector panel */}
        <Paper
          sx={{
            width: 230,
            flexShrink: 0,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 2,
            overflow: "hidden",
          }}
        >
          <Box sx={{ px: 2, py: 1.5, backgroundColor: "rgba(0,0,0,0.02)", borderBottom: "1px solid", borderColor: "divider" }}>
            <Typography variant="caption" sx={{ color: "text.secondary", fontWeight: 700, letterSpacing: "0.5px", textTransform: "uppercase", fontSize: "0.65rem" }}>
              Select Case
            </Typography>
          </Box>

          {isLoading ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
              <CircularProgress size={24} sx={{ color: "#8B4049" }} />
            </Box>
          ) : isError ? (
            <Box sx={{ px: 2, py: 3 }}>
              <Typography variant="body2" color="error">Error loading cases</Typography>
            </Box>
          ) : cases.length === 0 ? (
            <Box sx={{ px: 2, py: 3 }}>
              <Typography variant="body2" color="text.disabled">No cases found</Typography>
            </Box>
          ) : (
            <>
              <Box sx={{ maxHeight: 460, overflowY: "auto" }}>
                {cases.map((c) => {
                  const sc = STATUS_CONFIG[c.status];
                  const isActive = selectedCase?.id === c.id;
                  return (
                    <Box
                      key={c.id}
                      onClick={() => setSelectedCase(c)}
                      sx={{
                        px: 2,
                        py: 1.25,
                        cursor: "pointer",
                        borderLeft: isActive ? "3px solid #8B4049" : "3px solid transparent",
                        backgroundColor: isActive ? "rgba(139,64,73,0.05)" : "transparent",
                        "&:hover": { backgroundColor: isActive ? "rgba(139,64,73,0.07)" : "rgba(0,0,0,0.02)" },
                        borderBottom: "1px solid",
                        borderColor: "divider",
                      }}
                    >
                      <Typography
                        variant="caption"
                        sx={{ fontFamily: "monospace", fontWeight: 700, color: isActive ? "#8B4049" : "text.primary", display: "block", mb: 0.5 }}
                      >
                        {c.caseReference}
                      </Typography>
                      <Chip
                        label={sc?.label ?? c.status}
                        size="small"
                        sx={{
                          backgroundColor: sc?.bgColor ?? "#f5f5f5",
                          color: sc?.color ?? "#666",
                          fontWeight: 500,
                          fontSize: "0.65rem",
                          height: 18,
                          borderRadius: 1,
                        }}
                      />
                    </Box>
                  );
                })}
              </Box>

              {/* Pagination */}
              {totalPages > 1 && (
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 1, py: 1, borderTop: "1px solid", borderColor: "divider" }}>
                  <Button
                    size="small"
                    disabled={page === 0}
                    onClick={() => { setPage((p) => p - 1); setSelectedCase(null); }}
                    sx={{ textTransform: "none", minWidth: 0, px: 1, fontSize: "0.7rem" }}
                  >
                    Prev
                  </Button>
                  <Typography variant="caption" color="text.secondary">
                    {page + 1} / {totalPages}
                  </Typography>
                  <Button
                    size="small"
                    disabled={page + 1 >= totalPages}
                    onClick={() => { setPage((p) => p + 1); setSelectedCase(null); }}
                    sx={{ textTransform: "none", minWidth: 0, px: 1, fontSize: "0.7rem" }}
                  >
                    Next
                  </Button>
                </Box>
              )}
            </>
          )}
        </Paper>

        {/* Graph canvas */}
        <Paper
          sx={{
            flex: 1,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 2,
            overflow: "hidden",
            position: "relative",
          }}
        >
          {/* Case header */}
          {selectedCase ? (
            <Box
              sx={{
                px: 2,
                py: 1.5,
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                backgroundColor: "rgba(0,0,0,0.02)",
                borderBottom: "1px solid",
                borderColor: "divider",
              }}
            >
              <CaseIcon sx={{ color: "#8B4049", fontSize: 18 }} />
              <Typography variant="body2" sx={{ fontFamily: "monospace", fontWeight: 700, color: "text.primary" }}>
                {selectedCase.caseReference}
              </Typography>
              <Chip
                label={STATUS_CONFIG[selectedCase.status]?.label ?? selectedCase.status}
                size="small"
                sx={{
                  backgroundColor: STATUS_CONFIG[selectedCase.status]?.bgColor ?? "#f5f5f5",
                  color: STATUS_CONFIG[selectedCase.status]?.color ?? "#666",
                  fontWeight: 500,
                  fontSize: "0.7rem",
                  height: 20,
                  borderRadius: 1,
                }}
              />
              <Divider orientation="vertical" flexItem />
              <Typography variant="caption" color="text.secondary" sx={{ fontSize: "0.7rem" }}>
                {selectedCase.description?.slice(0, 60)}{selectedCase.description && selectedCase.description.length > 60 ? "…" : ""}
              </Typography>
              <Typography variant="caption" color="text.disabled" sx={{ ml: "auto", whiteSpace: "nowrap", fontSize: "0.65rem" }}>
                Drag to pan · Scroll to zoom · Click node for details
              </Typography>
            </Box>
          ) : (
            <Box sx={{ px: 2, py: 1.5, backgroundColor: "rgba(0,0,0,0.02)", borderBottom: "1px solid", borderColor: "divider" }}>
              <Typography variant="caption" color="text.disabled">Select a case to view its network graph</Typography>
            </Box>
          )}

          {/* Graph or placeholder */}
          {selectedCase ? (
            <CaseGraphInner key={selectedCase.id} caseId={selectedCase.id} />
          ) : (
            <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: CANVAS_H, gap: 1 }}>
              <CaseIcon sx={{ fontSize: 48, color: "text.disabled" }} />
              <Typography variant="body2" color="text.disabled">
                Select a case from the panel on the left
              </Typography>
            </Box>
          )}
        </Paper>
      </Box>
    </Box>
  );
}
