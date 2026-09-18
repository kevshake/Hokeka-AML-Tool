import type { ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "../../lib/utils";

const MAX_WIDTH: Record<string, string> = {
  sm: "max-w-sm",
  md: "max-w-lg",
  lg: "max-w-2xl",
  xl: "max-w-4xl",
};

export interface GlassModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
  maxWidth?: keyof typeof MAX_WIDTH;
  headerExtra?: ReactNode;
  bodyClassName?: string;
}

/** Glass-aligned modal shell — token text, hokeka-glass-card panel. */
export default function GlassModal({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  maxWidth = "md",
  headerExtra,
  bodyClassName,
}: GlassModalProps) {
  if (!open) return null;

  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-black/55 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <div
        className={cn(
          "fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2",
          MAX_WIDTH[maxWidth],
        )}
        role="dialog"
        aria-modal="true"
        aria-labelledby="glass-modal-title"
      >
        <div className="hokeka-glass-card overflow-hidden rounded-2xl shadow-editorial">
          <div className="flex items-start justify-between gap-3 border-b border-hairline px-6 py-4">
            <div className="min-w-0">
              <h3 id="glass-modal-title" className="font-display text-lg font-semibold tracking-tight text-ink">
                {title}
              </h3>
              {subtitle ? (
                <p className="mt-0.5 text-xs text-ink-muted">{subtitle}</p>
              ) : null}
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {headerExtra}
              <button
                type="button"
                onClick={onClose}
                className="rounded p-1 text-ink-muted transition-colors hover:bg-burgundy-800 hover:text-ink"
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>
          </div>
          <div className={cn("max-h-[70vh] overflow-y-auto px-6 py-4", bodyClassName)}>
            {children}
          </div>
          {footer ? (
            <div className="flex flex-wrap justify-end gap-2 border-t border-hairline px-6 py-3">
              {footer}
            </div>
          ) : null}
        </div>
      </div>
    </>
  );
}
