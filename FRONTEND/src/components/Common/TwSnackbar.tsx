import { X } from 'lucide-react'
import { useEffect } from 'react'
import { cn } from '../../lib/utils'

interface TwSnackbarProps {
  open: boolean
  message: string
  severity?: 'success' | 'error' | 'warning' | 'info'
  onClose: () => void
  autoHideDuration?: number
}

const SEVERITY_CLASSES = {
  success: 'border-emerald-700/30 bg-emerald-900/30 text-emerald-200',
  error: 'border-red-700/30 bg-red-900/30 text-red-200',
  warning: 'border-amber-700/30 bg-amber-900/30 text-amber-200',
  info: 'border-sky-700/30 bg-sky-900/30 text-sky-200',
}

export default function TwSnackbar({
  open,
  message,
  severity = 'info',
  onClose,
  autoHideDuration = 5000,
}: TwSnackbarProps) {
  useEffect(() => {
    if (!open) return
    const timer = setTimeout(onClose, autoHideDuration)
    return () => clearTimeout(timer)
  }, [open, onClose, autoHideDuration])

  if (!open) return null

  return (
    <div className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-fade-in">
      <div
        className={cn(
          'flex items-center gap-3 rounded-lg border px-4 py-3 shadow-lg backdrop-blur-sm',
          SEVERITY_CLASSES[severity],
        )}
      >
        <span className="text-sm">{message}</span>
        <button onClick={onClose} className="shrink-0 rounded p-0.5 transition-colors hover:bg-white/10">
          <X size={16} />
        </button>
      </div>
    </div>
  )
}