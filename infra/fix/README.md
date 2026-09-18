# FIX 4.4 market feed

The backend embeds QuickFIX/J and accepts either all-acceptor or all-initiator
sessions from one settings file. FIX is disabled unless `FIX_ENABLED=true`.

1. Copy `hokeka-fix44.cfg.example` to `hokeka-fix44.cfg`.
2. Replace the counterparty ID and credentials, then set the real PSP ID.
3. Keep `ResetOnLogon`, `ResetOnLogout`, and `ResetOnDisconnect` disabled so
   sequence recovery remains durable.
4. Ensure the configured `FileStorePath` and `FileLogPath` are writable by the
   backend container. Mount those paths on durable storage in production.
5. Set `FIX_ACCEPT_PORT` to the same host port used by the counterparty. The
   container listens on port 9878 in the supplied acceptor configuration.

The application validates every session before startup. It rejects blank
credentials, missing PSP ownership, mixed acceptor/initiator modes, and
non-persistent store or log paths.

Inbound application messages supported:

- `D` NewOrderSingle -> market order
- `F` OrderCancelRequest -> cancellation and spoofing assessment
- `8` ExecutionReport -> fill, partial fill, cancellation, or rejection

Custom tags map the external feed to internal customers and evidence. Every
message is stored as a sanitized field allowlist plus a SHA-256 hash; raw FIX
messages and logon credentials are never stored in the application database.
