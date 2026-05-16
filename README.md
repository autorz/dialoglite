# Dia Log Lite

A lightweight, responsive web application for personal time tracking and overtime management. Designed for privacy and simplicity, Dia Log Lite helps you keep track of your working hours, visualize your productivity trends, and estimate the monetary value of your overtime.

> [!WARNING]
> **Security Notice:** This application is designed with a **Zero Trust** architecture in mind. It does **not** include built-in authentication. It is intended to be deployed behind an identity-aware proxy or zero-trust gateway (e.g., Cloudflare Access, Tailscale Funnel, or Authelia). At the very least add basic http authentication on the webserver/proxy.

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
  - **Quick Update:** Edit the main periods of any day directly from the dashboard.
  - **Advanced Edit:** Manage multiple periods, add notes, toggle holiday status, or set manual balance overrides.
- **Holiday Detection:** Automatic detection of Brazilian holidays (SP subdivision) with the ability to manually flag any day as a holiday.
- **Responsive Design:** Optimized for both desktop and mobile viewing with a clean, modern UI based on the Catppuccin theme.
- **MCP Server Support:** Native `fastmcp` integration, exposing your data and tool-actions to local AI agents (SSE enabled on `/mcp/sse`).

## Tech Stack

- **Backend:** Python (3.12+), Flask, FastAPI / Starlette / FastMCP, Flask-SQLAlchemy (SQLite)
- **Frontend:** HTML5, Bootstrap 5, Chart.js (Local assets)
- **Environment:** Docker, Docker Compose, uv

## Getting Started

### Local Development

1. Install `uv` if you haven't already.
2. Sync dependencies:
   ```bash
   uv sync
   ```
3. Run the application:
   ```bash
   uv run uvicorn asgi:app --host 0.0.0.0 --port 8000
   ```
4. Open `http://localhost:8000` in your browser.
5. Provide the MCP server connection to your local AI Agent pointing to `http://localhost:8000/mcp/sse`.

## Configuration

- **Start Date:** Configure when your history begins in the settings modal.
- **Salary/Hours:** Set your monthly salary and expected monthly hours in the settings to enable monetary tooltips. This data stays on your device.
