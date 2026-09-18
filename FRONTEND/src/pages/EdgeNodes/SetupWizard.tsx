import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Link,
  MenuItem,
  Step,
  StepLabel,
  Stepper,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  ExternalLink,
  KeyRound,
  ShieldCheck,
  Terminal,
} from 'lucide-react';
import CopyButton from './CopyButton';
import KeyFingerprint from './KeyFingerprint';
import {
  formatAbsolute,
  formatRelative,
  hoursUntil,
  installCommand,
  MONO_FONT,
  pspLabel,
  slugifyEdgeId,
  type InstallPlatform,
} from './edgeMeta';
import { resolveEdgeError, useEdgeNodeDetail, useRequestNode } from './edgeApi';
import type { EdgeNodeCreatedResponse, EdgeNodeView } from './types';
import type { Psp } from '../../types';

const STEPS = ['Identity', 'Authorization', 'Install', 'Activation'];

const INSTALL_GUIDE_URL =
  (import.meta.env.VITE_EDGE_INSTALL_GUIDE_URL as string | undefined) ||
  `${window.location.origin}/docs/hokeka-edge-install-guide.pdf`;

interface SetupWizardProps {
  open: boolean;
  onClose: () => void;
  mode: 'create' | 'resume';
  isPlatformAdmin: boolean;
  ownPspId?: number;
  ownPspName?: string;
  psps?: Psp[];
  existingNode?: EdgeNodeView;
  controlPlaneUrl: string;
}

interface FormState {
  pspId: string;
  displayName: string;
  edgeId: string;
  hostname: string;
  controlPlane: string;
}

/** A monospace, horizontally-scrollable block with an inline copy control. */
function CodeBlock({ value, copyLabel }: { value: string; copyLabel?: string }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1,
        p: 1.25,
        borderRadius: 'var(--radius)',
        border: '1px solid var(--line)',
        backgroundColor: 'var(--surface-1)',
      }}
    >
      <Box
        component="pre"
        sx={{
          m: 0,
          flex: 1,
          minWidth: 0,
          overflowX: 'auto',
          fontFamily: MONO_FONT,
          fontSize: '0.8rem',
          color: 'var(--ink)',
          whiteSpace: 'pre',
        }}
      >
        {value}
      </Box>
      <CopyButton value={value} label={copyLabel} />
    </Box>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="overline" sx={{ color: 'var(--muted)', display: 'block', mb: 0.5 }}>
        {label}
      </Typography>
      {children}
    </Box>
  );
}

export default function SetupWizard({
  open,
  onClose,
  mode,
  isPlatformAdmin,
  ownPspId,
  ownPspName,
  psps,
  existingNode,
  controlPlaneUrl,
}: SetupWizardProps) {
  const requestMutation = useRequestNode();

  const [activeStep, setActiveStep] = useState(0);
  const [created, setCreated] = useState<EdgeNodeCreatedResponse | null>(null);
  const [platform, setPlatform] = useState<InstallPlatform>('linux');
  const [edgeIdTouched, setEdgeIdTouched] = useState(false);
  const [form, setForm] = useState<FormState>({
    pspId: '',
    displayName: '',
    edgeId: '',
    hostname: '',
    controlPlane: controlPlaneUrl,
  });

  // Fresh wizard on each open. The enrollment code is intentionally lost on close.
  useEffect(() => {
    if (!open) return;
    requestMutation.reset();
    setCreated(null);
    setPlatform('linux');
    setEdgeIdTouched(false);
    if (mode === 'resume' && existingNode) {
      setActiveStep(0);
      setForm({
        pspId: String(existingNode.pspId),
        displayName: existingNode.displayName,
        edgeId: existingNode.edgeId || slugifyEdgeId(existingNode.displayName),
        hostname: existingNode.hostname || '',
        controlPlane: controlPlaneUrl,
      });
    } else {
      setActiveStep(0);
      setForm({
        pspId: isPlatformAdmin ? '' : String(ownPspId ?? ''),
        displayName: '',
        edgeId: '',
        hostname: '',
        controlPlane: controlPlaneUrl,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // Keep the suggested edge id in sync with the display name until the user edits it.
  const effectiveEdgeId = edgeIdTouched
    ? form.edgeId
    : form.edgeId || slugifyEdgeId(form.displayName || 'edge-node');

  const nodeId = created?.node.id ?? existingNode?.id ?? null;
  const pollDetail = open && activeStep === 3;
  const detail = useEdgeNodeDetail(nodeId, pollDetail);
  const liveNode = detail.data?.node ?? created?.node ?? existingNode ?? null;

  const effectivePspId = useMemo(() => {
    if (created?.node.pspId != null) return created.node.pspId;
    if (existingNode?.pspId != null) return existingNode.pspId;
    return isPlatformAdmin ? Number(form.pspId) : ownPspId ?? '';
  }, [created, existingNode, isPlatformAdmin, form.pspId, ownPspId]);

  const enrollmentCode = created?.enrollmentCode ?? '<ENROLLMENT-CODE>';
  const command = installCommand(platform, {
    pspId: effectivePspId,
    edgeId: effectiveEdgeId.trim() || 'edge-node',
    enrollmentCode,
    controlPlaneUrl: form.controlPlane.trim() || controlPlaneUrl,
  });

  const lockIdentity = mode === 'create' && !!created;
  const identityValid = form.displayName.trim().length > 0 && (isPlatformAdmin ? !!form.pspId : !!ownPspId);

  const handleRequest = () => {
    const pspIdNum = isPlatformAdmin ? Number(form.pspId) : ownPspId;
    if (!pspIdNum || !form.displayName.trim()) return;
    if (!edgeIdTouched) setForm((f) => ({ ...f, edgeId: effectiveEdgeId }));
    requestMutation.mutate(
      { pspId: pspIdNum, displayName: form.displayName.trim() },
      {
        onSuccess: (resp) => {
          setCreated(resp);
          setActiveStep(1);
        },
      },
    );
  };

  const handleClose = () => {
    if (requestMutation.isPending) return;
    onClose();
  };

  /* ---- step bodies ------------------------------------------------------- */

  const identityStep = (
    <Box>
      <Typography variant="body2" sx={{ mb: 2, color: 'var(--muted)' }}>
        {mode === 'create'
          ? 'Name the node and confirm where it will run. Requesting it issues a single-use enrollment code and puts it in the approval queue.'
          : 'Node identity as recorded by the control plane.'}
      </Typography>

      {isPlatformAdmin ? (
        <Field label="Owning PSP">
          <TextField
            select
            fullWidth
            size="small"
            value={form.pspId}
            onChange={(e) => setForm((f) => ({ ...f, pspId: e.target.value }))}
            disabled={mode === 'resume' || lockIdentity}
            placeholder="Select a PSP"
          >
            <MenuItem value="" disabled>
              Select a PSP…
            </MenuItem>
            {(psps ?? []).map((p) => (
              <MenuItem key={p.id} value={String(p.id)}>
                {pspLabel(p)}
              </MenuItem>
            ))}
          </TextField>
        </Field>
      ) : (
        <Field label="Owning PSP">
          <Typography variant="body2" sx={{ color: 'var(--ink)' }}>
            {ownPspName || `PSP ${ownPspId ?? ''}`}
          </Typography>
        </Field>
      )}

      <Field label="Display name">
        <TextField
          fullWidth
          size="small"
          value={form.displayName}
          onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
          disabled={mode === 'resume' || lockIdentity}
          placeholder="e.g. Acme — EU primary site"
        />
      </Field>

      <Field label="Edge id (stable machine identity)">
        <TextField
          fullWidth
          size="small"
          value={effectiveEdgeId}
          onChange={(e) => {
            setEdgeIdTouched(true);
            setForm((f) => ({ ...f, edgeId: e.target.value }));
          }}
          helperText="Configured on the edge host and pinned at activation. Lower-case, no spaces."
          placeholder="acme-eu-1"
        />
      </Field>

      <Field label="Control-plane URL">
        <TextField
          fullWidth
          size="small"
          value={form.controlPlane}
          onChange={(e) => setForm((f) => ({ ...f, controlPlane: e.target.value }))}
          helperText="Where the edge posts its enrollment and pulls bundles."
        />
      </Field>

      {requestMutation.isError && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {resolveEdgeError(requestMutation.error)}
        </Alert>
      )}
    </Box>
  );

  const authorizationStep = (
    <Box>
      {created ? (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
            <KeyRound size={18} style={{ color: 'var(--gold)' }} />
            <Typography variant="h6" sx={{ m: 0 }}>
              One-time enrollment code
            </Typography>
          </Box>
          <Alert severity="warning" icon={<AlertTriangle size={18} />} sx={{ mb: 2 }}>
            This code is shown once and expires in 24 hours. It authorizes exactly one node and cannot
            be retrieved again — copy it now.
          </Alert>

          <CodeBlock value={created.enrollmentCode} copyLabel="Copy code" />

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 1.5, color: 'var(--muted)' }}>
            <Clock size={15} />
            <Typography variant="body2" sx={{ color: 'var(--muted)' }}>
              Expires {formatRelative(created.enrollmentCodeExpiresAt)} ·{' '}
              {formatAbsolute(created.enrollmentCodeExpiresAt)}
              {hoursUntil(created.enrollmentCodeExpiresAt) != null &&
                ` (~${hoursUntil(created.enrollmentCodeExpiresAt)}h)`}
            </Typography>
          </Box>

          <Typography variant="caption" sx={{ display: 'block', mt: 1.5, color: 'var(--muted-2)' }}>
            {created.notice}
          </Typography>
        </>
      ) : (
        <Alert severity="info">
          The enrollment code was shown only once, when this node was requested, and the control plane
          keeps only its hash — it cannot be shown again. If it was lost, revoke this node and request
          a replacement.
        </Alert>
      )}
    </Box>
  );

  const installStep = (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
        <Terminal size={18} style={{ color: 'var(--teal)' }} />
        <Typography variant="h6" sx={{ m: 0 }}>
          Install on the edge host
        </Typography>
      </Box>

      <ToggleButtonGroup
        exclusive
        size="small"
        value={platform}
        onChange={(_e, v: InstallPlatform | null) => v && setPlatform(v)}
        sx={{ mb: 1.5 }}
      >
        <ToggleButton value="linux">Linux</ToggleButton>
        <ToggleButton value="windows">Windows</ToggleButton>
      </ToggleButtonGroup>

      <CodeBlock value={command} copyLabel="Copy command" />

      {!created && (
        <Typography variant="caption" sx={{ display: 'block', mt: 1, color: 'var(--warning)' }}>
          Replace &lt;ENROLLMENT-CODE&gt; with the code issued when this node was requested.
        </Typography>
      )}

      <Divider sx={{ my: 2 }} />

      <Typography variant="overline" sx={{ color: 'var(--muted)' }}>
        Prerequisites
      </Typography>
      <Box component="ul" sx={{ pl: 2.5, m: 0, mb: 1.5, color: 'var(--muted)', fontSize: '0.82rem', lineHeight: 1.7 }}>
        <li>Linux (Debian/Ubuntu, RHEL/Rocky/Alma, SUSE, Arch, Alpine) or Windows Server / 10 / 11.</li>
        <li>The installer provisions the container runtime (or use Linux <code>--native</code> for Temurin JRE 25 + Aerospike CE).</li>
        <li>Outbound HTTPS to the control plane; the edge API binds to 127.0.0.1 by default.</li>
        <li>Run as root (Linux) or Administrator (Windows).</li>
      </Box>

      <Link
        href={INSTALL_GUIDE_URL}
        target="_blank"
        rel="noopener noreferrer"
        sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontSize: '0.82rem' }}
      >
        Full install guide & sizing (PDF)
        <ExternalLink size={14} />
      </Link>
    </Box>
  );

  const status = liveNode?.status;
  const activationStep = (
    <Box sx={{ textAlign: 'center', py: 2 }}>
      {status === 'ACTIVE' ? (
        <>
          <CheckCircle2 size={40} style={{ color: 'var(--teal)' }} />
          <Typography variant="h6" sx={{ mt: 1 }}>
            Node activated
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--muted)', mb: 2 }}>
            Keys are pinned and rule bundles are now flowing. Verify the fingerprints below with the
            operator out-of-band.
          </Typography>
          <Box sx={{ textAlign: 'left', display: 'grid', gap: 1.5, maxWidth: 460, mx: 'auto' }}>
            <KeyFingerprint label="X25519 (encryption)" base64Key={liveNode?.edgeX25519PublicKey} />
            <KeyFingerprint label="Ed25519 (signing)" base64Key={liveNode?.edgeEd25519PublicKey} />
          </Box>
        </>
      ) : status === 'REVOKED' || status === 'REJECTED' || status === 'SUSPENDED' ? (
        <>
          <AlertTriangle size={40} style={{ color: 'var(--danger)' }} />
          <Typography variant="h6" sx={{ mt: 1 }}>
            Node is {status.toLowerCase()}
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--muted)' }}>
            This node will not activate in its current state.
          </Typography>
        </>
      ) : (
        <>
          <CircularProgress size={34} />
          <Typography variant="h6" sx={{ mt: 1.5 }}>
            {status === 'APPROVED' ? 'Approved — waiting for the node to activate' : 'Awaiting Hokeka approval'}
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--muted)' }}>
            {status === 'APPROVED'
              ? 'Run the installer on the edge host. This flips to activated automatically the moment the node completes first-boot enrollment.'
              : 'A platform administrator must approve this node before it can activate. This updates automatically.'}
          </Typography>
        </>
      )}
    </Box>
  );

  const stepBody = [identityStep, authorizationStep, installStep, activationStep][activeStep];

  /* ---- footer ------------------------------------------------------------ */

  const canGoBack = activeStep > 0 && !(activeStep === 1 && lockIdentity);

  let primary: ReactNode;
  if (activeStep === 0 && mode === 'create') {
    primary = (
      <Button
        variant="contained"
        color="primary"
        onClick={handleRequest}
        disabled={!identityValid || requestMutation.isPending}
        startIcon={requestMutation.isPending ? <CircularProgress size={15} color="inherit" /> : <ShieldCheck size={16} />}
      >
        Request node
      </Button>
    );
  } else if (activeStep < 3) {
    primary = (
      <Button variant="contained" color="primary" onClick={() => setActiveStep((s) => s + 1)}>
        Next
      </Button>
    );
  } else {
    primary = (
      <Button variant="contained" color="primary" onClick={handleClose}>
        Done
      </Button>
    );
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
      <DialogTitle>{mode === 'create' ? 'Authorize a new edge node' : `Set up · ${form.displayName}`}</DialogTitle>
      <DialogContent sx={{ pt: 2 }}>
        <Stepper activeStep={activeStep} alternativeLabel sx={{ mb: 3 }}>
          {STEPS.map((label) => (
            <Step key={label}>
              <StepLabel>{label}</StepLabel>
            </Step>
          ))}
        </Stepper>
        {stepBody}
      </DialogContent>
      <DialogActions>
        {canGoBack && (
          <Button variant="text" onClick={() => setActiveStep((s) => Math.max(0, s - 1))}>
            Back
          </Button>
        )}
        <Box sx={{ flex: 1 }} />
        <Button variant="text" onClick={handleClose} disabled={requestMutation.isPending}>
          {activeStep === 3 ? 'Close' : 'Cancel'}
        </Button>
        {primary}
      </DialogActions>
    </Dialog>
  );
}
