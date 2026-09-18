import { useState, type ReactNode } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Ban,
  CheckCircle2,
  Download,
  Lock,
  Pause,
  ShieldAlert,
  X,
} from 'lucide-react';
import { HealthChip, StatusChip } from './EdgeChips';
import KeyFingerprint from './KeyFingerprint';
import ConfirmActionDialog from './ConfirmActionDialog';
import { resolveEdgeError, useEdgeNodeDetail, useNodeAction } from './edgeApi';
import {
  countRuleHits,
  formatAbsolute,
  formatCount,
  formatMicros,
  formatRelative,
  MONO_FONT,
} from './edgeMeta';
import type { EdgeMetricsRecord, EdgeNodeView } from './types';

/* ---- small presentational helpers (local, not exported) ------------------ */

function Panel({ title, icon, children }: { title: string; icon?: ReactNode; children: ReactNode }) {
  return (
    <Box
      sx={{
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        backgroundColor: 'var(--surface-2)',
        p: 2,
        mb: 2,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1.5 }}>
        {icon}
        <Typography variant="overline" sx={{ color: 'var(--gold)', letterSpacing: '0.14em' }}>
          {title}
        </Typography>
      </Box>
      {children}
    </Box>
  );
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, py: 0.6, minWidth: 0 }}>
      <Typography variant="body2" sx={{ color: 'var(--muted)', flexShrink: 0 }}>
        {label}
      </Typography>
      <Box sx={{ color: 'var(--ink)', fontSize: '0.82rem', textAlign: 'right', minWidth: 0, overflow: 'hidden' }}>
        {children}
      </Box>
    </Box>
  );
}

function StatTile({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <Box
      sx={{
        border: '1px solid var(--line)',
        borderRadius: 'var(--radius)',
        backgroundColor: 'var(--surface-1)',
        px: 1.25,
        py: 1,
        textAlign: 'center',
      }}
    >
      <Typography sx={{ fontFamily: MONO_FONT, fontSize: '1.05rem', fontWeight: 600, color }}>
        {value}
      </Typography>
      <Typography variant="caption" sx={{ color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
        {label}
      </Typography>
    </Box>
  );
}

// The control plane binds exactly two HSE-1 contexts (EdgeBundleDistributionService.BUNDLE_CONTEXT
// and EdgeMetricsIngestService.METRICS_CONTEXT). Enrollment/activation is plain JSON, not a sealed
// envelope, so there is no third context — do not list one that does not exist.
const HSE1_CONTEXTS = [
  { purpose: 'Rule bundle distribution', ctx: 'hokeka.rules.bundle' },
  { purpose: 'Aggregate metrics upload', ctx: 'hokeka.metrics.report' },
];

interface NodeDetailDrawerProps {
  open: boolean;
  nodeId: number | null;
  onClose: () => void;
  /** Open the setup wizard for a not-yet-active node. */
  onResumeSetup: (node: EdgeNodeView) => void;
}

export default function NodeDetailDrawer({ open, nodeId, onClose, onResumeSetup }: NodeDetailDrawerProps) {
  const detail = useEdgeNodeDetail(nodeId, open);
  const action = useNodeAction();
  const [confirm, setConfirm] = useState<'suspend' | 'revoke' | null>(null);

  const node = detail.data?.node ?? null;
  const metrics: EdgeMetricsRecord[] = detail.data?.recentMetrics ?? [];
  const latest = metrics[0];

  const terminal = node?.status === 'REVOKED' || node?.status === 'REJECTED';
  const encrypted =
    node?.status === 'ACTIVE' && !!node.edgeX25519PublicKey && !!node.edgeEd25519PublicKey;

  const runAction = (kind: 'suspend' | 'revoke') => {
    if (!node) return;
    action.mutate(
      { id: node.id, action: kind },
      {
        onSuccess: () => {
          setConfirm(null);
        },
      },
    );
  };

  const closeConfirm = () => {
    if (action.isPending) return;
    setConfirm(null);
    action.reset();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 540 }, maxWidth: '100%' } }}
    >
      <Box sx={{ p: 2.5, height: '100%', overflowY: 'auto' }}>
        {/* header */}
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1, mb: 2 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h5" sx={{ mb: 0.5, wordBreak: 'break-word' }}>
              {node?.displayName ?? 'Edge node'}
            </Typography>
            <Typography sx={{ fontFamily: MONO_FONT, fontSize: '0.78rem', color: 'var(--muted)' }}>
              {node?.edgeId ?? 'edge id assigned at activation'}
            </Typography>
          </Box>
          <IconButton onClick={onClose} aria-label="Close panel">
            <X size={18} />
          </IconButton>
        </Box>

        {detail.isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={28} />
          </Box>
        ) : detail.isError || !node ? (
          <Alert severity="error">{resolveEdgeError(detail.error) || 'Could not load this node.'}</Alert>
        ) : (
          <>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
              <StatusChip status={node.status} />
              <HealthChip health={node.health} />
            </Box>

            {(node.status === 'PENDING' || node.status === 'APPROVED') && (
              <Button
                variant="outlined"
                color="primary"
                startIcon={<Download size={16} />}
                onClick={() => onResumeSetup(node)}
                sx={{ mb: 2 }}
              >
                Setup & install instructions
              </Button>
            )}

            {/* Channel panel — the point of the exercise (UX spec §5) */}
            <Panel title="Secure channel" icon={<Lock size={15} style={{ color: 'var(--gold)' }} />}>
              {encrypted ? (
                <Alert severity="success" icon={<CheckCircle2 size={18} />} sx={{ mb: 1.5 }}>
                  Verified end-to-end — TLS 1.3 (mutual TLS) transport with an HSE-1 encrypted,
                  signed payload. Keys are pinned.
                </Alert>
              ) : node.status === 'SUSPENDED' ? (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                  Distribution paused — this node is suspended and is not receiving bundles.
                </Alert>
              ) : terminal ? (
                <Alert severity="error" icon={<ShieldAlert size={18} />} sx={{ mb: 1.5 }}>
                  Channel terminated — this node is {node.status.toLowerCase()}. No bundles are served.
                </Alert>
              ) : (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                  Channel not yet established — the node has not completed activation, so nothing is
                  encrypted end-to-end yet.
                </Alert>
              )}

              <InfoRow label="Transport">TLS 1.3 · mutual TLS</InfoRow>
              <InfoRow label="Payload">HSE-1 · X25519 → HKDF-SHA256 → ChaCha20-Poly1305 · Ed25519</InfoRow>
              <Divider sx={{ my: 1 }} />
              <Box sx={{ display: 'grid', gap: 1.25, my: 1 }}>
                <KeyFingerprint
                  label="X25519 public key (encryption)"
                  base64Key={node.edgeX25519PublicKey}
                  caption="Pinned trust-on-first-activation"
                />
                <KeyFingerprint
                  label="Ed25519 public key (signing)"
                  base64Key={node.edgeEd25519PublicKey}
                />
              </Box>
              <Divider sx={{ my: 1 }} />
              <Typography variant="caption" sx={{ color: 'var(--muted)', display: 'block', mb: 0.5 }}>
                Bound HSE-1 contexts
              </Typography>
              {HSE1_CONTEXTS.map((c) => (
                <InfoRow key={c.ctx} label={c.purpose}>
                  <Box component="span" sx={{ fontFamily: MONO_FONT }}>
                    {c.ctx}
                  </Box>
                </InfoRow>
              ))}
              <Divider sx={{ my: 1 }} />
              <InfoRow label="Last poll / bundle pull">
                {node.lastSeenAt ? (
                  <Tooltip title={formatAbsolute(node.lastSeenAt)}>
                    <span>{formatRelative(node.lastSeenAt)}</span>
                  </Tooltip>
                ) : (
                  'never'
                )}
              </InfoRow>
              <InfoRow label="Last metrics upload">
                {latest ? (
                  <Tooltip title={formatAbsolute(latest.windowEnd)}>
                    <span>{formatRelative(latest.windowEnd)}</span>
                  </Tooltip>
                ) : (
                  'none'
                )}
              </InfoRow>
            </Panel>

            {/* Rule bundle */}
            <Panel title="Rule bundle">
              <InfoRow label="Active version">
                {node.lastBundleVersion != null ? `v${node.lastBundleVersion}` : '—'}
              </InfoRow>
              <InfoRow label="Bundle hash">
                {latest?.ruleBundleHash ? (
                  <Tooltip title={latest.ruleBundleHash}>
                    <Box component="span" sx={{ fontFamily: MONO_FONT }}>
                      {latest.ruleBundleHash.slice(0, 16)}…
                    </Box>
                  </Tooltip>
                ) : (
                  '—'
                )}
              </InfoRow>
              <InfoRow label="Delivered">{formatRelative(node.lastSeenAt)}</InfoRow>
              <InfoRow label="Rules hit (last window)">
                {countRuleHits(latest?.ruleHitCountsJson) ?? '—'}
              </InfoRow>
            </Panel>

            {/* Aggregate metrics */}
            <Panel title="Aggregate metrics">
              <Alert severity="info" sx={{ mb: 1.5 }}>
                Aggregate counts and latencies only — no transaction data ever leaves the PSP premises.
              </Alert>
              {latest ? (
                <>
                  <Typography variant="caption" sx={{ color: 'var(--muted)', display: 'block', mb: 1 }}>
                    Window {formatAbsolute(latest.windowStart)} → {formatAbsolute(latest.windowEnd)}
                  </Typography>
                  <Box
                    sx={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(84px, 1fr))',
                      gap: 1,
                      mb: 1.5,
                    }}
                  >
                    <StatTile label="Evaluated" value={formatCount(latest.totalEvaluated)} color="var(--ink)" />
                    <StatTile label="Allowed" value={formatCount(latest.allowed)} color="var(--decision-allow)" />
                    <StatTile label="Alerted" value={formatCount(latest.alerted)} color="var(--decision-alert)" />
                    <StatTile label="Held" value={formatCount(latest.held)} color="var(--decision-hold)" />
                    <StatTile label="Blocked" value={formatCount(latest.blocked)} color="var(--decision-block)" />
                  </Box>
                  <InfoRow label="Latency p50 / p95 / p99">
                    {formatMicros(latest.p50LatencyMicros)} · {formatMicros(latest.p95LatencyMicros)} ·{' '}
                    {formatMicros(latest.p99LatencyMicros)}
                  </InfoRow>
                  {latest.engineHealth && (
                    <InfoRow label="Engine health">
                      <Chip size="small" variant="outlined" label={latest.engineHealth} />
                    </InfoRow>
                  )}
                  <Typography variant="caption" sx={{ color: 'var(--muted-2)', display: 'block', mt: 1 }}>
                    Showing the most recent of {metrics.length} window{metrics.length === 1 ? '' : 's'}.
                  </Typography>
                </>
              ) : (
                <Typography variant="body2" sx={{ color: 'var(--muted)' }}>
                  No metrics windows received yet.
                </Typography>
              )}
            </Panel>

            {/* Danger zone */}
            {!terminal && (
              <Panel title="Danger zone" icon={<ShieldAlert size={15} style={{ color: 'var(--danger)' }} />}>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {node.status === 'ACTIVE' && (
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<Pause size={16} />}
                      onClick={() => setConfirm('suspend')}
                    >
                      Suspend
                    </Button>
                  )}
                  <Button
                    variant="contained"
                    color="error"
                    startIcon={<Ban size={16} />}
                    onClick={() => setConfirm('revoke')}
                  >
                    Revoke
                  </Button>
                </Box>
                <Typography variant="caption" sx={{ color: 'var(--muted)', display: 'block', mt: 1.25 }}>
                  Suspend stops distribution on the next poll. There is no un-suspend transition —
                  restoring service means enrolling a new node. Revoke is permanent.
                </Typography>
              </Panel>
            )}
          </>
        )}
      </Box>

      <ConfirmActionDialog
        open={confirm === 'suspend'}
        onClose={closeConfirm}
        onConfirm={() => runAction('suspend')}
        title="Suspend edge node"
        confirmLabel="Suspend node"
        confirmColor="warning"
        loading={action.isPending}
        errorMessage={action.isError ? resolveEdgeError(action.error) : null}
        description={
          <>
            Bundle distribution stops on this node&apos;s next poll. There is no un-suspend
            transition — the enrollment code was consumed at activation, so restoring service means
            enrolling a new node. Continue?
          </>
        }
      />

      <ConfirmActionDialog
        open={confirm === 'revoke'}
        onClose={closeConfirm}
        onConfirm={() => runAction('revoke')}
        title="Revoke edge node"
        confirmLabel="Revoke permanently"
        confirmColor="error"
        typedConfirmation={node?.displayName}
        loading={action.isPending}
        errorMessage={action.isError ? resolveEdgeError(action.error) : null}
        description={
          <>
            This is <strong>irreversible</strong>. Distribution stops immediately and the node can
            never be reactivated. Its pinned keys are kept on the revoked record so the same edge
            identity can never be reused — recovery means enrolling a new node with fresh keys.
          </>
        }
      />
    </Drawer>
  );
}
