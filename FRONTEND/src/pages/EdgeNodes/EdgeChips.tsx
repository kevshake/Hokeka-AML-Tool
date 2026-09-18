import { Chip, Tooltip } from '@mui/material';
import { healthMeta, statusMeta } from './edgeMeta';
import type { EdgeHealth, EdgeNodeStatus } from './types';

/**
 * Status chip. Colours come exclusively from the shared MUI palette slots that
 * ThemeContext binds to tokens (gold/teal/amber/red/neutral) — never hardcoded here.
 */
export function StatusChip({
  status,
  size = 'small',
}: {
  status: EdgeNodeStatus | string;
  size?: 'small' | 'medium';
}) {
  const meta = statusMeta(status);
  return <Chip size={size} color={meta.color} label={meta.label} />;
}

/** Secondary operational-health chip (HEALTHY / STALE / NEVER_SEEN / …). */
export function HealthChip({
  health,
  size = 'small',
}: {
  health: EdgeHealth | string;
  size?: 'small' | 'medium';
}) {
  const meta = healthMeta(health);
  return (
    <Tooltip title="Operational health derived from status and last-seen time">
      <Chip size={size} variant="outlined" color={meta.color} label={meta.label} />
    </Tooltip>
  );
}
