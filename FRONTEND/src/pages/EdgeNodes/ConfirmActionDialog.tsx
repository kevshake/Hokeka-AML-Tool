import { useEffect, useState, type ReactNode } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  Typography,
} from '@mui/material';
import { MONO_FONT } from './edgeMeta';

interface ConfirmActionDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  confirmColor?: 'error' | 'warning' | 'primary';
  /** When set, the confirm button stays disabled until the user types this exactly. */
  typedConfirmation?: string;
  loading?: boolean;
  errorMessage?: string | null;
}

/**
 * MUI confirmation dialog — never a native window.confirm. Revoke passes a
 * `typedConfirmation` so the irreversible action requires deliberately re-typing
 * the node's name (UX spec §5 danger zone).
 */
export default function ConfirmActionDialog({
  open,
  onClose,
  onConfirm,
  title,
  description,
  confirmLabel,
  confirmColor = 'error',
  typedConfirmation,
  loading = false,
  errorMessage,
}: ConfirmActionDialogProps) {
  const [typed, setTyped] = useState('');

  // Reset the typed guard whenever the dialog (re)opens.
  useEffect(() => {
    if (open) setTyped('');
  }, [open]);

  const matches = !typedConfirmation || typed.trim() === typedConfirmation;

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent sx={{ pt: 2 }}>
        <Box sx={{ color: 'var(--muted)', fontSize: '0.85rem', lineHeight: 1.55 }}>{description}</Box>

        {typedConfirmation && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="body2" sx={{ mb: 0.75, color: 'var(--muted)' }}>
              Type{' '}
              <Box component="span" sx={{ fontFamily: MONO_FONT, color: 'var(--ink)' }}>
                {typedConfirmation}
              </Box>{' '}
              to confirm.
            </Typography>
            <TextField
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              size="small"
              fullWidth
              autoComplete="off"
              placeholder={typedConfirmation}
              disabled={loading}
            />
          </Box>
        )}

        {errorMessage && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {errorMessage}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          variant="contained"
          color={confirmColor}
          onClick={onConfirm}
          disabled={loading || !matches}
          startIcon={loading ? <CircularProgress size={15} color="inherit" /> : undefined}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
