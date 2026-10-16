// Sim-only stand-in for the production nginx edge front end (BACKEND/nginx/*.conf):
//   * terminates TLS 1.3 on https://127.0.0.1:8444 with a sim-CA server cert (SAN localhost/127.0.0.1)
//   * requests (but does not require) a client certificate and verifies it against the sim CA
//   * forwards to the Control Plane at http://127.0.0.1:2637 from loopback (a trusted proxy) with
//     X-SSL-Client-Verify / X-SSL-Client-S-DN exactly like nginx's $ssl_client_verify /
//     $ssl_client_s_dn, plus X-Forwarded-Proto: https so security.require-ssl passes.
// Inbound copies of the X-SSL-* headers are stripped so a client cannot forge them.
// Usage: node scripts/sim/stack/mtls-proxy.mjs <certDir> [listenPort] [upstream]
import https from 'node:https';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const certDir = process.argv[2];
const port = Number(process.argv[3] || 8444);
const upstream = new URL(process.argv[4] || `http://127.0.0.1:${process.env.SIM_CP_PORT || 2637}`);
if (!certDir) {
  console.error('usage: node mtls-proxy.mjs <certDir> [port] [upstream]');
  process.exit(2);
}
const read = (f) => fs.readFileSync(path.join(certDir, f));

const server = https.createServer({
  key: read('proxy-server.key'),
  cert: read('proxy-server.crt'),
  ca: read('ca.crt'),
  requestCert: true,
  rejectUnauthorized: false,
  minVersion: 'TLSv1.3',
}, (req, res) => {
  const sock = req.socket;
  const peer = sock.getPeerCertificate();
  const hasCert = peer && Object.keys(peer).length > 0;
  let verify = 'NONE';
  let dn = '';
  if (hasCert) {
    verify = sock.authorized ? 'SUCCESS' : `FAILED:${sock.authorizationError}`;
    const s = peer.subject || {};
    // RFC 2253 order like nginx: most specific first.
    dn = ['CN', 'OU', 'O', 'L', 'ST', 'C']
      .filter((k) => s[k])
      .map((k) => `${k}=${Array.isArray(s[k]) ? s[k][0] : s[k]}`)
      .join(',');
  }
  const headers = { ...req.headers };
  for (const h of Object.keys(headers)) {
    if (h.startsWith('x-ssl-client-')) delete headers[h];
  }
  headers['x-ssl-client-verify'] = verify;
  if (dn) headers['x-ssl-client-s-dn'] = dn;
  headers['x-forwarded-proto'] = 'https';
  headers['x-forwarded-for'] = sock.remoteAddress;
  headers.host = upstream.host;
  const started = Date.now();
  const preq = http.request({
    host: upstream.hostname, port: upstream.port, method: req.method, path: req.url, headers,
  }, (pres) => {
    res.writeHead(pres.statusCode, pres.headers);
    pres.pipe(res);
    pres.on('end', () => console.log(`${new Date().toISOString()} ${req.method} ${req.url} -> ${pres.statusCode} verify=${verify} dn="${dn}" ${Date.now() - started}ms`));
  });
  preq.on('error', (e) => {
    console.log(`${new Date().toISOString()} ${req.method} ${req.url} -> 502 ${e.message}`);
    res.writeHead(502, { 'content-type': 'text/plain' });
    res.end('bad gateway: ' + e.message);
  });
  req.pipe(preq);
});
server.listen(port, '127.0.0.1', () => console.log(`mtls-proxy listening https://127.0.0.1:${port} -> ${upstream.href}`));
