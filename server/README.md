# Dia Log Lite — server

> This is the Flask/ASGI web app of the [Dia Log Lite monorepo](../README.md). Everything in this document assumes you are inside `server/`.

A lightweight, responsive web application for personal time tracking and overtime management. Designed for privacy and simplicity, Dia Log Lite helps you keep track of your working hours, visualize your productivity trends, and estimate the monetary value of your overtime.

> [!WARNING]
> **Security Notice:** This application is designed with a **Zero Trust** architecture in mind. It does **not** include built-in authentication. It is intended to be deployed behind an identity-aware proxy or zero-trust gateway (e.g., Cloudflare Access, Tailscale Funnel, or Authelia). At the very least add basic http authentication on the webserver/proxy.
>
> **The MCP server endpoint at `/mcp` is also unauthenticated** and allows full read/write access to your data (notes, periods, settings). If you expose the service beyond `localhost`, gate `/mcp` the same way you gate the rest of the app, or block it at the proxy layer.

## Features

- **Automated Workday Simulation:** Automatically populates default work hours (09:00 - 12:00, 13:00 - 18:00) for standard workdays.
- **Accurate Overtime Calculation:**
  - Standard workdays: 1x hour calculation against an expected 8 hours.
  - Sundays and Holidays: 1.5x overtime multiplier with 0 expected hours.
- **Dynamic Dashboard & Analytics:**
  - **Live Balance:** View your current accumulated overtime balance at a glance.
  - **Performance Stats:** Track average arrival time, departure time, and daily delta across 7, 30, and 90-day windows.
  - **Future Projections:** Forecast your accumulated balance for the next 90 days based on historical averages.
  - **Interactive Charts:** Visualize your balance history and 7-day moving average with an integrated line chart.
- **Privacy-First Monetary Estimation:** Tooltips display the monetary value of your time based on your salary. All salary data is stored **locally in your browser** and never touches the server.
- **Flexible Entry Management:**
  - **Quick Update:** Edit the main periods and the daily note for any row directly from the dashboard. Modified rows are highlighted and a single **Save** click commits every changed row in one shot.
  - **Advanced Edit:** Manage multiple periods, toggle holiday status, or set manual balance overrides.
- **Holiday Detection:** Automatic detection of Brazilian holidays (SP subdivision) with the ability to manually flag any day as a holiday.
- **Responsive Design:** Optimized for both desktop and mobile viewing with a clean, modern UI based on the Catppuccin theme.
- **MCP Server Support:** Native `fastmcp` integration over the streamable-HTTP transport at `/mcp`, exposing your data and tool actions to AI clients (Claude, Claude Code, VS Code, etc.).

## Tech Stack

- **Backend:** Python (3.12+), Flask, Starlette / FastMCP (ASGI via Uvicorn), Flask-SQLAlchemy (SQLite)
- **Frontend:** HTML5, Bootstrap 5, Chart.js (Local assets)
- **Environment:** Docker, Docker Compose, uv

## Getting Started

### Running with Docker Compose

1. Clone the repository.
2. Start the application from this directory:
   ```bash
   cd server
   docker compose up -d
   ```
3. Access the dashboard at `http://localhost:8000`.

The build context is `server/` — the repository root is **not** a valid Docker
context for this image.

### Using the Pre-built Image

If you don't want to build the image yourself, you can use the official image from GitHub Container Registry.

#### Docker Run
```bash
docker run -d \
  --name dialoglite \
  -p 8000:8000 \
  -v $(pwd)/data:/app/data \
  -e DATABASE_URI=sqlite:////app/data/app.db \
  --health-cmd "python3 -c 'import urllib.request; urllib.request.urlopen(\"http://localhost:8000\")'" \
  --health-interval 30s \
  --health-timeout 10s \
  --health-retries 3 \
  ghcr.io/autorz/dialoglite:latest
```

#### Docker Compose
Create a `docker-compose.yml` file:
```yaml
services:
  app:
    image: ghcr.io/autorz/dialoglite:latest
    container_name: dialoglite
    ports:
      - "8000:8000"
    volumes:
      - ./data:/app/data
    environment:
      - DATABASE_URI=sqlite:////app/data/app.db
    restart: always
    healthcheck:
      test: ["CMD", "python3", "-c", "import urllib.request; urllib.request.urlopen('http://localhost:8000')"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s
```
Then run:
```bash
docker compose up -d
```

### Local Development

1. Install `uv` if you haven't already.
2. Sync dependencies (from `server/`, where `pyproject.toml` lives):
   ```bash
   cd server
   uv sync
   ```
3. Run the application:
   ```bash
   uv run uvicorn asgi:app --host 0.0.0.0 --port 8000
   ```
4. Open `http://localhost:8000` in your browser.
5. Point your AI client's MCP configuration at `http://localhost:8000/mcp` — see [Connecting AI Clients (MCP)](#connecting-ai-clients-mcp).

### Running the tests

```bash
cd server
uv run pytest
```

`tests/` is a package (`tests/__init__.py`), so pytest inserts `server/` on
`sys.path` and `from app.core import ...` resolves regardless of the directory
you invoke it from.

## Connecting AI Clients (MCP)

Dialoglite exposes a Model Context Protocol server using the **streamable-HTTP** transport. The endpoint is:

```
http://<your-host>:8000/mcp
```

> [!IMPORTANT]
> The endpoint is **unauthenticated** and grants full read/write access to your data. Only expose it through an identity-aware proxy (Cloudflare Access, Tailscale, Authelia…) or keep it on `localhost` / your private network. Replace `http://` with `https://` once you put it behind TLS.

### Claude (claude.ai)

1. Open **Settings → Connectors → Add custom connector** (the menu may also appear as *Integrations* depending on your plan).
2. Paste the URL of your `/mcp` endpoint as the **Remote MCP server URL** and give it a name (e.g. `Dialoglite`).
3. Save and authorize. The Dialoglite tools will appear in any new chat.

Custom connectors require a paid Claude plan.

### Claude Code (CLI)

Add the server with a single command:

```bash
claude mcp add --transport http dialoglite https://your-host/mcp
```

Or commit a project-scoped `.mcp.json` so collaborators pick it up automatically:

```json
{
  "mcpServers": {
    "dialoglite": {
      "type": "http",
      "url": "https://your-host/mcp"
    }
  }
}
```

Verify with `claude mcp list`.

### VS Code (GitHub Copilot Chat — agent mode)

Create `.vscode/mcp.json` in your workspace (or add the same JSON under `chat.mcp.servers` in your user `settings.json` for global use):

```json
{
  "servers": {
    "dialoglite": {
      "type": "http",
      "url": "https://your-host/mcp"
    }
  }
}
```

VS Code 1.97+ picks this up automatically and the Dialoglite tools become available in Copilot Chat's agent mode.

## Configuration

- **Start Date:** Configure when your history begins in the settings modal.
- **Salary/Hours:** Set your monthly salary and expected monthly hours in the settings to enable monetary tooltips. This data stays on your device.
