import { useState } from 'react';
import { Button, IconButton, Tooltip } from '@mui/material';
import { Check, Copy } from 'lucide-react';

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* fall through to the legacy path */
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

interface CopyButtonProps {
  value: string;
  /** Render a full labelled button instead of a bare icon. */
  label?: string;
  size?: 'small' | 'medium';
  ariaLabel?: string;
}

export default function CopyButton({ value, label, size = 'small', ariaLabel }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);

  const onCopy = async () => {
    const ok = await copyText(value);
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    }
  };

  if (label) {
    return (
      <Button
        size={size}
        variant="outlined"
        color={copied ? 'success' : 'primary'}
        onClick={onCopy}
        startIcon={copied ? <Check size={15} /> : <Copy size={15} />}
      >
        {copied ? 'Copied' : label}
      </Button>
    );
  }

  return (
    <Tooltip title={copied ? 'Copied' : 'Copy'}>
      <IconButton
        size={size}
        onClick={onCopy}
        aria-label={ariaLabel ?? 'Copy to clipboard'}
        sx={{ color: copied ? 'var(--teal)' : undefined }}
      >
        {copied ? <Check size={16} /> : <Copy size={16} />}
      </IconButton>
    </Tooltip>
  );
}
