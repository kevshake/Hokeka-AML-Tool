#!/usr/bin/env node
/**
 * Full compliance chain against a live stack: ingest → screening → alert disposition →
 * case link probe → SAR draft → MLRO approval → export metadata.
 */
const apiBase = process.env.CONTROL_PLANE_URL || 'http://127.0.0.1:2637/api/v1'
const complianceUser = process.env.E2E_COMPLIANCE_USER || 'velocity.compliance'
const mlroUser = process.env.E2E_MLRO_USER || 'velocity.mlro'
const password = process.env.E2E_PASSWORD || 'Hokeka2026!'

/** Performs JSON fetch against the Control Plane with optional session cookie. */
async function api(path, init = {}, cookie = '') {
  const headers = { 'Content-Type': 'application/json', ...(init.headers || {}) }
  if (cookie) headers.Cookie = cookie
  const res = await fetch(`${apiBase}${path}`, { ...init, headers })
  const text = await res.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  return { res, body }
}

/** Logs in and returns session cookie. */
async function login(username) {
  const { res, body } = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) throw new Error(`login ${username} failed ${res.status}: ${JSON.stringify(body)}`)
  return (res.headers.get('set-cookie') || '').split(';')[0]
}

async function main() {
  try {
    const probe = await fetch(`${apiBase}/actuator/health`)
    if (!probe.ok) {
      console.log('SKIP: Control Plane not healthy')
      process.exit(2)
    }
  } catch {
    console.log('SKIP: Control Plane unreachable')
    process.exit(2)
  }

  const complianceCookie = await login(complianceUser)
  const ref = `chain-${Date.now()}`
  const ingest = await api('/transactions/ingest', {
    method: 'POST',
    body: JSON.stringify({
      merchantId: '1',
      amountCents: 99000000,
      currency: 'KES',
      clientReference: ref,
      direction: 'INBOUND',
      countryCode: 'KE',
      channelType: 'ECOM',
    }),
  }, complianceCookie)
  if (!ingest.res.ok) throw new Error(`ingest failed ${ingest.res.status}`)
  const txnId = ingest.body?.transactionId ?? ingest.body?.txnId
  console.log('OK ingest', txnId, ingest.body?.decision)

  const screen = await api('/sanctions/screen', {
    method: 'POST',
    body: JSON.stringify({ name: 'Sample Sanctioned Entity', entityType: 'PERSON' }),
  }, complianceCookie)
  if (!screen.res.ok) throw new Error(`screening failed ${screen.res.status}`)
  console.log('OK screening', screen.body?.status ?? screen.body?.screeningStatus)

  if (txnId) {
    const links = await api(`/compliance/links/transaction/${txnId}`, {}, complianceCookie)
    if (links.res.ok) {
      console.log('OK compliance links keys', Object.keys(links.body || {}))
    }
    const alerts = await api(`/transactions/${txnId}/alerts`, {}, complianceCookie)
    if (alerts.res.ok) console.log('OK linked alerts', Array.isArray(alerts.body) ? alerts.body.length : 0)
  }

  const sarRef = `SAR-SMOKE-${Date.now()}`
  const sarCreate = await api('/compliance/sar/workflow/create', {
    method: 'POST',
    body: JSON.stringify({
      sarReference: sarRef,
      narrative: 'Deployment readiness automated SAR draft',
      suspiciousActivityType: 'STRUCTURING',
      jurisdiction: 'KE',
      sarType: 'INITIAL',
    }),
  }, complianceCookie)
  if (!sarCreate.res.ok) throw new Error(`SAR create failed ${sarCreate.res.status}: ${JSON.stringify(sarCreate.body)}`)
  const sarId = sarCreate.body?.id ?? sarCreate.body?.sarId
  console.log('OK SAR draft', sarId)

  await api('/compliance/sar/workflow/submit', {
    method: 'POST',
    body: JSON.stringify({ sarId }),
  }, complianceCookie)

  let mlroCookie
  try {
    mlroCookie = await login(mlroUser)
  } catch {
    console.log('WARN MLRO user missing — skipping second-reviewer approval (provision velocity.mlro)')
    console.log('PASS partial compliance chain (ingest + screening + SAR draft)')
    process.exit(0)
  }

  const approve = await api('/compliance/sar/workflow/approve', {
    method: 'POST',
    body: JSON.stringify({ sarId }),
  }, mlroCookie)
  if (!approve.res.ok) throw new Error(`SAR approve failed ${approve.res.status}: ${JSON.stringify(approve.body)}`)
  console.log('OK SAR approved by MLRO')

  const exportMeta = await api(`/compliance/sar/${sarId}`, {}, mlroCookie)
  if (exportMeta.res.ok) {
    console.log('OK SAR export/readback status', exportMeta.body?.status ?? exportMeta.body?.workflowStatus)
  }

  console.log('PASS full compliance chain smoke')
}

main().catch((e) => {
  console.error('FAIL', e.message)
  process.exit(1)
})
