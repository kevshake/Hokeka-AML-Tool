import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import { Ban, Check, Download, Eye, Pause, Plus, RefreshCw, Server, X } from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';
import HokekaPageShell from '../../components/Layout/HokekaPageShell';
import { useAuth } from '../../contexts/AuthContext';
import { useAllPsps } from '../../features/api/queries';
import { HealthChip, StatusChip } from './EdgeChips';
import ConfirmActionDialog from './ConfirmActionDialog';
import NodeDetailDrawer from './NodeDetailDrawer';
import SetupWizard from './SetupWizard';
import { edgeKeys, resolveEdgeError, useEdgeNodes, useNodeAction } from './edgeApi';
import { formatAbsolute, formatRelative, isPlatformAdmin, pspLabel, statusBlurb } from './edgeMeta';
import type { EdgeNodeView, NodeAction } from './types';

const CONTROL_PLANE_URL = typeof window !== 'undefined' ? window.location.origin : '';

interface ActionMeta {
  title: string;
  confirmLabel: string;
  color: 'error' | 'warning' | 'primary';
  typed: boolean;
  description: string;
}

const ACTION_META: Record<NodeAction, ActionMeta> = {
  approve: {
    title: 'Approve edge node',
    confirmLabel: 'Approve',
    color: 'primary',
    typed: false,
    description:
      'This authorizes the node to complete activation and begin receiving rule bundles. The owning PSP must itself be ACTIVE.',
  },
  reject: {
    title: 'Reject edge node',
    confirmLabel: 'Reject',
    color: 'error',
    typed: false,
    description:
      'This permanently rejects the request and destroys its enrollment code. It cannot be undone.',
  },
  suspend: {
    title: 'Suspend edge node',
    confirmLabel: 'Suspend',
    color: 'warning',
    typed: false,
    description:
      'Bundle distribution stops on the next poll. There is no un-suspend transition — restoring service means enrolling a new node.',
  },
  revoke: {
    title: 'Revoke edge node',
    confirmLabel: 'Revoke permanently',
    color: 'error',
    typed: true,
    description:
      'This is irreversible. Distribution stops immediately and the node must re-enroll with fresh keys.',
  },
};

export default function EdgeNodesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const platformAdmin = isPlatformAdmin(user?.role?.name);

  const [pspFilter, setPspFilter] = useState<number | ''>('');
  const filterValue = platformAdmin && pspFilter !== '' ? pspFilter : undefined;

  const { data: nodes, isLoading, isError, error } = useEdgeNodes(filterValue);
  const { data: psps } = useAllPsps();
  const action = useNodeAction();

  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardMode, setWizardMode] = useState<'create' | 'resume'>('create');
  const [resumeNode, setResumeNode] = useState<EdgeNodeView | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [target, setTarget] = useState<{ node: EdgeNodeView; action: NodeAction } | null>(null);

  const pspNameById = useMemo(() => {
    const map = new Map<number, string>();
    (psps ?? []).forEach((p) => {
      if (p.id != null) map.set(p.id, pspLabel(p));
    });
    return map;
  }, [psps]);

  const openCreate = () => {
    setResumeNode(null);
    setWizardMode('create');
    setWizardOpen(true);
  };

  const openResume = (node: EdgeNodeView) => {
    setResumeNode(node);
    setWizardMode('resume');
    setWizardOpen(true);
  };

  const runTargetAction = () => {
    if (!target) return;
    action.mutate(
      { id: target.node.id, action: target.action },
      { onSuccess: () => setTarget(null) },
    );
  };

  const closeTarget = () => {
    if (action.isPending) return;
    setTarget(null);
    action.reset();
  };

  const refresh = () => queryClient.invalidateQueries({ queryKey: edgeKeys.all });

  const rows = nodes ?? [];

  const headerActions = (
    <>
      {platformAdmin && (
        <Select
          size="small"
          value={pspFilter === '' ? 'all' : String(pspFilter)}
          onChange={(e) => setPspFilter(e.target.value === 'all' ? '' : Number(e.target.value))}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="all">All PSPs</MenuItem>
          {(psps ?? []).map((p) => (
            <MenuItem key={p.id} value={String(p.id)}>
              {pspLabel(p)}
            </MenuItem>
          ))}
        </Select>
      )}
      <Tooltip title="Refresh">
        <IconButton onClick={refresh} aria-label="Refresh fleet">
          <RefreshCw size={17} />
        </IconButton>
      </Tooltip>
      <Button variant="contained" color="primary" startIcon={<Plus size={16} />} onClick={openCreate}>
        Request node
      </Button>
    </>
  );

  return (
    <HokekaPageShell
      title="Edge Nodes"
      subtitle="Authorize and manage PSP on-prem edge engines"
      actions={headerActions}
      noCard
    >
      {isError ? (
        <Alert severity="error">{resolveEdgeError(error) || 'Could not load the edge fleet.'}</Alert>
      ) : (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <TableContainer sx={{ maxHeight: 'calc(100vh - 260px)' }}>
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Node</TableCell>
                  {platformAdmin && <TableCell>PSP</TableCell>}
                  <TableCell>Status</TableCell>
                  <TableCell>Health</TableCell>
                  <TableCell>Last seen</TableCell>
                  <TableCell>Bundle</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={platformAdmin ? 7 : 6} align="center" sx={{ py: 6 }}>
                      <CircularProgress size={26} />
                    </TableCell>
                  </TableRow>
                ) : rows.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={platformAdmin ? 7 : 6} align="center" sx={{ py: 6 }}>
                      <Server size={28} style={{ color: 'var(--muted-2)' }} />
                      <Typography variant="body2" sx={{ color: 'var(--muted)', mt: 1 }}>
                        No edge nodes yet. Request one to begin authorization.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  rows.map((node) => {
                    const terminal = node.status === 'REVOKED' || node.status === 'REJECTED';
                    return (
                      <TableRow
                        key={node.id}
                        hover
                        sx={{ cursor: 'pointer' }}
                        onClick={() => setDetailId(node.id)}
                      >
                        <TableCell>
                          <Typography variant="body2" sx={{ color: 'var(--ink)', fontWeight: 600 }}>
                            {node.displayName}
                          </Typography>
                          <Typography
                            sx={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '0.72rem', color: 'var(--muted-2)' }}
                          >
                            {node.edgeId ?? '— not activated —'}
                          </Typography>
                        </TableCell>

                        {platformAdmin && (
                          <TableCell sx={{ color: 'var(--muted)' }}>
                            {pspNameById.get(node.pspId) ?? `PSP ${node.pspId}`}
                          </TableCell>
                        )}

                        <TableCell>
                          <StatusChip status={node.status} />
                          <Typography variant="caption" sx={{ display: 'block', color: 'var(--muted-2)', mt: 0.5 }}>
                            {statusBlurb(node.status, {
                              lastSeenAt: node.lastSeenAt,
                              enrollmentCodeExpiresAt: node.enrollmentCodeExpiresAt,
                              healthy: node.health === 'HEALTHY',
                            })}
                          </Typography>
                        </TableCell>

                        <TableCell>
                          <HealthChip health={node.health} />
                        </TableCell>

                        <TableCell sx={{ color: 'var(--muted)' }}>
                          {node.lastSeenAt ? (
                            <Tooltip title={formatAbsolute(node.lastSeenAt)}>
                              <span>{formatRelative(node.lastSeenAt)}</span>
                            </Tooltip>
                          ) : (
                            '—'
                          )}
                        </TableCell>

                        <TableCell sx={{ color: 'var(--muted)' }}>
                          {node.lastBundleVersion != null ? `v${node.lastBundleVersion}` : '—'}
                        </TableCell>

                        <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                          <Box sx={{ display: 'inline-flex', gap: 0.25 }}>
                            <Tooltip title="Channel & details">
                              <IconButton size="small" onClick={() => setDetailId(node.id)} aria-label="Open details">
                                <Eye size={16} />
                              </IconButton>
                            </Tooltip>

                            {platformAdmin && node.status === 'PENDING' && (
                              <>
                                <Tooltip title="Approve">
                                  <IconButton
                                    size="small"
                                    sx={{ color: 'var(--teal)' }}
                                    onClick={() => setTarget({ node, action: 'approve' })}
                                    aria-label="Approve node"
                                  >
                                    <Check size={16} />
                                  </IconButton>
                                </Tooltip>
                                <Tooltip title="Reject">
                                  <IconButton
                                    size="small"
                                    sx={{ color: 'var(--danger)' }}
                                    onClick={() => setTarget({ node, action: 'reject' })}
                                    aria-label="Reject node"
                                  >
                                    <X size={16} />
                                  </IconButton>
                                </Tooltip>
                              </>
                            )}

                            {(node.status === 'PENDING' || node.status === 'APPROVED') && (
                              <Tooltip title="Setup & install">
                                <IconButton
                                  size="small"
                                  sx={{ color: 'var(--gold)' }}
                                  onClick={() => openResume(node)}
                                  aria-label="Setup node"
                                >
                                  <Download size={16} />
                                </IconButton>
                              </Tooltip>
                            )}

                            {node.status === 'ACTIVE' && (
                              <Tooltip title="Suspend">
                                <IconButton
                                  size="small"
                                  sx={{ color: 'var(--warning)' }}
                                  onClick={() => setTarget({ node, action: 'suspend' })}
                                  aria-label="Suspend node"
                                >
                                  <Pause size={16} />
                                </IconButton>
                              </Tooltip>
                            )}

                            {!terminal && (
                              <Tooltip title="Revoke">
                                <IconButton
                                  size="small"
                                  sx={{ color: 'var(--danger)' }}
                                  onClick={() => setTarget({ node, action: 'revoke' })}
                                  aria-label="Revoke node"
                                >
                                  <Ban size={16} />
                                </IconButton>
                              </Tooltip>
                            )}
                          </Box>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      <SetupWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        mode={wizardMode}
        isPlatformAdmin={platformAdmin}
        ownPspId={user?.pspId}
        ownPspName={user?.psp?.name}
        psps={psps}
        existingNode={wizardMode === 'resume' ? resumeNode ?? undefined : undefined}
        controlPlaneUrl={CONTROL_PLANE_URL}
      />

      <NodeDetailDrawer
        open={detailId != null}
        nodeId={detailId}
        onClose={() => setDetailId(null)}
        onResumeSetup={(node) => {
          setDetailId(null);
          openResume(node);
        }}
      />

      {target && (
        <ConfirmActionDialog
          open
          onClose={closeTarget}
          onConfirm={runTargetAction}
          title={ACTION_META[target.action].title}
          confirmLabel={ACTION_META[target.action].confirmLabel}
          confirmColor={ACTION_META[target.action].color}
          typedConfirmation={ACTION_META[target.action].typed ? target.node.displayName : undefined}
          loading={action.isPending}
          errorMessage={action.isError ? resolveEdgeError(action.error) : null}
          description={ACTION_META[target.action].description}
        />
      )}
    </HokekaPageShell>
  );
}
