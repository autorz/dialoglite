# Personal Time Tracker

A Python web application with a simple, responsive UI to record and manage personal work hours, breaks, and overtime balance. Designed for local or personal hosting, using SQLite for a straightforward setup.

## Features

- **Automated Workday Simulation:** Assumes default work hours (09:00 - 12:00, 13:00 - 18:00) for standard Mon-Fri workdays.
- **Accurate Overtime Calculation:**
  - Standard workdays: 1x hour calculation against an expected 8 hours.
  - Sundays and Holidays: 1.5x overtime multiplier, with an expected 0 hours.
- **Manual Balance Overrides:** Adjust your accumulated overtime easily on any specific day.
- **Holiday Detection:** Automatically flags Brazilian holidays (SP subdivision) leveraging the python `holidays` library. Includes support to toggle arbitrary days as manual holidays.
- **Flexible Entries:** Add or remove arbitrary periods (e.g., crossing midnight or short shifts).
- **Responsive Dashboard:** View your entire history, period adjustments, notes, and up-to-date accumulated balances directly on the dashboard.

## Tech Stack

- **Backend:** Python (3.12+), Flask, Flask-SQLAlchemy (SQLite)
- **Frontend:** HTML, Bootstrap 5 (Local assets, no CDNs)
- **Deployment:** Docker, Docker Compose
- **Environment Management:** uv

## Running with Docker Compose

1. Clone the repository.
2. Build and start the service:
   ```bash
   docker compose up -d
   ```
3. Access the application via `http://localhost:8000`.

## Local Development Setup

1. Make sure you have `uv` installed.
2. Install dependencies and activate virtual environment:
   ```bash
   uv sync
   ```
3. Run the development server:
   ```bash
   uv run python main.py
   ```
4. Access the application via `http://localhost:8000`.
