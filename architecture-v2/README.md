# ⚠️ ARCHIVAL — NOT A PRODUCT RUNTIME

**`architecture-v2/` is reference material only.** It is **not** part of the supported Hokeka AML deployment topology.

The single supported runtime is documented in [`docs/SYSTEM-GLOSSARY.md`](../docs/SYSTEM-GLOSSARY.md):

- **Console** — `FRONTEND/`
- **Control Plane** — `BACKEND/` + `aml-microservice/`
- **Edge Node** — `edge-host/` + `edge-engine/`

Do **not** use `architecture-v2/infra/docker-compose*.yml` for production or PSP installs. `docker-compose.prod.yml` at the repo root does not start any V2 engines.

Historical design notes: [`docs/ARCHITECTURE-V2.md`](docs/ARCHITECTURE-V2.md).
