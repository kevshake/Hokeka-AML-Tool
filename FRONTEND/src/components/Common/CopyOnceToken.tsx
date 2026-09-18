import { useState } from "react";
import { AlertTriangle, Check, Copy } from "lucide-react";
import { cn } from "../../lib/utils";

interface CopyOnceTokenProps {
  token: string;
  title?: string;
  hint?: string;
  onDismiss?: () => void;
  className?: string;
}

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* fall through */
  }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}

/** One-time secret display — copy affordance, not a raw Alert dump. */
export default function CopyOnceToken({
  token,
  title = "Copy this token now",
  hint = "Shown once. It is not stored in plaintext and cannot be retrieved again.",
  onDismiss,
  className,
}: CopyOnceTokenProps) {
  const [copied, setCopied] = useState(false);

  const onCopy = async () => {
    const ok = await copyText(token);
    if (ok) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    }
  };

  return (
    <div
      className={cn(
        "overflow-hidden rounded-xl border border-warning/35 bg-warning-soft/40",
        className,
      )}
      role="status"
    >
      <div className="flex items-start gap-2.5 border-b border-warning/25 px-4 py-3">
        <AlertTriangle size={16} className="mt-0.5 shrink-0 text-warning" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-ink">{title}</p>
          <p className="mt-0.5 text-xs leading-relaxed text-ink-muted">{hint}</p>
        </div>
        {onDismiss ? (
          <button
            type="button"
            onClick={onDismiss}
            className="shrink-0 text-xs text-ink-muted transition hover:text-ink"
            aria-label="Dismiss"
          >
            Dismiss
          </button>
        ) : null}
      </div>

      <div className="flex items-stretch gap-2 p-3">
        <pre
          className="min-w-0 flex-1 overflow-x-auto rounded-md border border-hairline bg-surface-1 px-3 py-2.5 font-mono text-[0.78rem] leading-relaxed text-ink"
          aria-label="Secret token"
        >
          {token}
        </pre>
        <button
          type="button"
          onClick={onCopy}
          className={cn(
            "inline-flex shrink-0 items-center gap-1.5 self-start rounded-md border px-3 py-2 text-xs font-semibold transition",
            copied
              ? "border-success/40 bg-success-soft text-success"
              : "border-hairline-strong bg-surface-2 text-gold hover:border-gold/40 hover:bg-surface-3",
          )}
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
          {copied ? "Copied" : "Copy"}
        </button>
      </div>
    </div>
  );
}
