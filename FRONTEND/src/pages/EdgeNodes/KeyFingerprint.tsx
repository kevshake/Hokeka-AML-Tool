import { useEffect, useState } from 'react';
import { Box, Skeleton, Tooltip, Typography } from '@mui/material';
import { AlertTriangle, Fingerprint } from 'lucide-react';
import CopyButton from './CopyButton';
import { keyFingerprint, MONO_FONT, type Fingerprint as FingerprintData } from './edgeMeta';

interface KeyFingerprintProps {
  label: string;
  base64Key: string | null | undefined;
  /** What this key is used for, shown as a caption. */
  caption?: string;
}

/**
 * Renders a short SHA-256 fingerprint of a pinned public key (never the raw blob).
 * A missing key or a compute failure renders as an explicit error state — a channel
 * that cannot be verified must never look like a neutral blank (UX spec §5).
 */
export default function KeyFingerprint({ label, base64Key, caption }: KeyFingerprintProps) {
  const [fp, setFp] = useState<FingerprintData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    setFp(null);
    setError(null);
    if (!base64Key) return;
    keyFingerprint(base64Key)
      .then((result) => {
        if (alive) setFp(result);
      })
      .catch((e: unknown) => {
        if (alive) setError(e instanceof Error ? e.message : 'Could not compute fingerprint');
      });
    return () => {
      alive = false;
    };
  }, [base64Key]);

  return (
    <Box>
      <Typography variant="overline" sx={{ color: 'var(--muted)', letterSpacing: '0.12em' }}>
        {label}
      </Typography>

      {!base64Key ? (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'var(--warning)' }}>
          <AlertTriangle size={15} />
          <Typography variant="body2" sx={{ color: 'var(--warning)' }}>
            Not pinned yet — key is recorded when the node activates.
          </Typography>
        </Box>
      ) : error ? (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, color: 'var(--danger)' }}>
          <AlertTriangle size={15} />
          <Typography variant="body2" sx={{ color: 'var(--danger)' }}>
            {error}
          </Typography>
        </Box>
      ) : !fp ? (
        <Skeleton variant="text" width={260} height={22} />
      ) : (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0 }}>
          <Fingerprint size={15} style={{ flexShrink: 0, color: 'var(--teal)' }} />
          <Tooltip title={fp.hex} placement="top">
            <Typography
              sx={{
                fontFamily: MONO_FONT,
                fontSize: '0.8rem',
                color: 'var(--ink)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {fp.short}
            </Typography>
          </Tooltip>
          <CopyButton value={fp.short} ariaLabel={`Copy ${label} fingerprint`} />
        </Box>
      )}

      {caption && (
        <Typography variant="caption" sx={{ color: 'var(--muted-2)', display: 'block' }}>
          {caption}
        </Typography>
      )}
    </Box>
  );
}
