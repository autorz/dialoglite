# Dia Log Lite

Monorepo for **Dia Log Lite**, a lightweight, privacy-first time tracking and
overtime tool.

| Directory | What it is | Docs |
|---|---|---|
| [`server/`](server/) | The Flask/ASGI web app + MCP server. Published as the container image `ghcr.io/autorz/dialoglite`. | [`server/README.md`](server/README.md) |

Everything else at the repository root is shared across the whole monorepo:
[`CHANGELOG.md`](CHANGELOG.md) (one timeline — releases are cut at the repo
level), [`.gitignore`](.gitignore), and [`.github/`](.github/) (CI).

> [!WARNING]
> **Security notice:** the server has **no built-in authentication**, and its
> MCP endpoint at `/mcp` grants full read/write access to your data. Deploy it
> behind an identity-aware proxy. See [`server/README.md`](server/README.md)
> for the full notice.

## Quick start

```bash
cd server
docker compose up -d   # dashboard on http://localhost:8000
```

Or use the pre-built multi-arch image (`linux/amd64` + `linux/arm64`):
`ghcr.io/autorz/dialoglite:latest`. Deployment recipes live in
[`server/README.md`](server/README.md).

## Repository layout

```
.
├── .github/workflows/   CI: builds and publishes the image on each release
├── CHANGELOG.md         one timeline for the whole repo
└── server/              Flask app — Docker build context lives HERE
```

The Docker build context is `server/`, not the root. The `Dockerfile` ends with
`COPY . /app`, so a root context would drag every future sibling directory into
the published image. Keeping `.dockerignore` and the `Dockerfile` next to the
code they describe keeps that blast radius closed.
