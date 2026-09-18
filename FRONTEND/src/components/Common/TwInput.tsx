import { cn } from '../../lib/utils'
import type { InputHTMLAttributes, SelectHTMLAttributes } from 'react'

interface TwInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
}

export function TwInput({ label, className, id, ...props }: TwInputProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={id} className="hokeka-field-label">
          {label}
        </label>
      )}
      <input id={id} className={cn('hokeka-field', className)} {...props} />
    </div>
  )
}

interface TwSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  options: { value: string; label: string }[]
}

export function TwSelect({ label, options, className, id, ...props }: TwSelectProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label htmlFor={id} className="hokeka-field-label">
          {label}
        </label>
      )}
      <select id={id} className={cn('hokeka-field', className)} {...props}>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  )
}
