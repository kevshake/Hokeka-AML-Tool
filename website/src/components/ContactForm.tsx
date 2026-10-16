import { FormEvent, useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { PUBLIC_API_BASE } from '../lib/motion'

type FormState = {
  fullName: string
  workEmail: string
  organization: string
  phone: string
  inquiryType: string
  message: string
}

const initial: FormState = {
  fullName: '',
  workEmail: '',
  organization: '',
  phone: '',
  inquiryType: 'DEMO',
  message: '',
}

/** Demo / contact form posting to the Control Plane public marketing endpoint. */
export default function ContactForm() {
  const [form, setForm] = useState<FormState>(initial)
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [error, setError] = useState<string | null>(null)

  /** Merge a single field change into local form state. */
  const onChange = (field: keyof FormState, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  /** POST validated inquiry JSON to the public website contact API. */
  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setStatus('submitting')
    setError(null)
    try {
      const response = await fetch(`${PUBLIC_API_BASE}/public/website/contact`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(form),
      })
      if (!response.ok) {
        const body = await response.text()
        throw new Error(body || `Request failed (${response.status})`)
      }
      setStatus('success')
      setForm(initial)
    } catch (err) {
      setStatus('error')
      setError(err instanceof Error ? err.message : 'Unable to send your message.')
    }
  }

  return (
    <form className="contact-form reveal" onSubmit={onSubmit} noValidate>
      <div className="contact-form-grid">
        <label>
          Full name
          <input
            required
            maxLength={200}
            value={form.fullName}
            onChange={(e) => onChange('fullName', e.target.value)}
            autoComplete="name"
          />
        </label>
        <label>
          Work email
          <input
            required
            type="email"
            maxLength={320}
            value={form.workEmail}
            onChange={(e) => onChange('workEmail', e.target.value)}
            autoComplete="email"
          />
        </label>
        <label>
          Organization
          <input
            maxLength={300}
            value={form.organization}
            onChange={(e) => onChange('organization', e.target.value)}
            autoComplete="organization"
          />
        </label>
        <label>
          Phone <span className="optional">(optional)</span>
          <input
            maxLength={50}
            value={form.phone}
            onChange={(e) => onChange('phone', e.target.value)}
            autoComplete="tel"
          />
        </label>
      </div>
      <label className="contact-message">
        How can we help?
        <textarea
          required
          minLength={10}
          maxLength={4000}
          rows={4}
          value={form.message}
          onChange={(e) => onChange('message', e.target.value)}
        />
      </label>
      <div className="contact-form-actions">
        <button className="button" type="submit" disabled={status === 'submitting'}>
          {status === 'submitting' ? 'Sending…' : 'Send message'}
          <ArrowRight size={16} />
        </button>
        {status === 'success' && (
          <p className="contact-form-note success" role="status">
            Thank you — our team will respond shortly.
          </p>
        )}
        {status === 'error' && (
          <p className="contact-form-note error" role="alert">
            {error ?? 'Something went wrong. You can also email info@hokeka.com.'}
          </p>
        )}
      </div>
    </form>
  )
}
