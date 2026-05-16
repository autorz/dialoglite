from fastmcp import FastMCP
from typing import List, Optional, Tuple, Dict, Any
from datetime import date, time
from pydantic import BaseModel, Field
import json
from .models import db, DayRecord, TimePeriod
from .core import (
    get_settings,
    update_settings_all,
    get_history_with_balances,
    get_dashboard_stats,
    update_day_periods,
    auto_populate_days
)

# We need a reference to the flask app to provide context for database operations
_flask_app = None

def set_flask_app(app):
    global _flask_app
    _flask_app = app

mcp = FastMCP("Dialoglite MCP")

def with_app_context(func):
    """Decorator to ensure the function runs within a Flask app context."""
    def wrapper(*args, **kwargs):
        if _flask_app is None:
            raise RuntimeError("Flask app not configured for MCP server")
        with _flask_app.app_context():
            return func(*args, **kwargs)

    # FastMCP uses signature inspection, so we need to preserve signature
    # However, for FastMCP.tool(), it's better to manually wrap the logic inside the tools
    return wrapper

@mcp.tool()
def get_settings_tool() -> dict:
    """Get the current application settings."""
    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        settings = get_settings()
        return {
            "start_date": settings.start_date.isoformat() if settings.start_date else None,
            "default_entry": settings.default_entry.isoformat() if settings.default_entry else None,
            "default_lunch_start": settings.default_lunch_start.isoformat() if settings.default_lunch_start else None,
            "default_lunch_end": settings.default_lunch_end.isoformat() if settings.default_lunch_end else None,
            "default_exit": settings.default_exit.isoformat() if settings.default_exit else None,
        }

@mcp.tool()
def update_settings_tool(
    start_date: str,
    default_entry: str,
    default_lunch_start: str,
    default_lunch_end: str,
    default_exit: str
) -> str:
    """
    Update the application settings.
    Dates should be YYYY-MM-DD. Times should be HH:MM:SS or HH:MM.
    """
    from datetime import datetime

    sd = datetime.strptime(start_date, "%Y-%m-%d").date()
    # Handle both HH:MM and HH:MM:SS
    def parse_time(t_str):
        try:
            return datetime.strptime(t_str, "%H:%M:%S").time()
        except ValueError:
            return datetime.strptime(t_str, "%H:%M").time()

    de = parse_time(default_entry)
    dls = parse_time(default_lunch_start)
    dle = parse_time(default_lunch_end)
    dx = parse_time(default_exit)

    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        update_settings_all(sd, de, dls, dle, dx)
        return "Settings updated successfully."

@mcp.tool()
def get_history_tool() -> dict:
    """Get the history of all day records and the current balance."""
    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        auto_populate_days()
        history, current_balance = get_history_with_balances()

        # Serialize datetime objects for JSON returning
        serialized_history = []
        for day in history:
            serialized_day = day.copy()
            serialized_day['date'] = day['date'].isoformat()
            serialized_periods = []
            for p in day['periods']:
                serialized_periods.append({
                    "entry_time": p.entry_time.isoformat() if p.entry_time else None,
                    "exit_time": p.exit_time.isoformat() if p.exit_time else None
                })
            serialized_day['periods'] = serialized_periods
            serialized_history.append(serialized_day)

        return {
            "current_balance": current_balance,
            "days": serialized_history
        }

@mcp.tool()
def get_stats_tool() -> dict:
    """Get the dashboard statistics including averages and forecasts."""
    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        history, current_balance = get_history_with_balances()
        stats = get_dashboard_stats(history, current_balance)
        return stats

@mcp.tool()
def get_day_record_tool(date_str: str) -> dict:
    """
    Get the details of a specific day record.
    date_str should be YYYY-MM-DD.
    """
    from datetime import datetime

    target_date = datetime.strptime(date_str, "%Y-%m-%d").date()

    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        auto_populate_days()
        history, _ = get_history_with_balances()

        for day in history:
            if day['date'] == target_date:
                serialized_day = day.copy()
                serialized_day['date'] = day['date'].isoformat()
                serialized_periods = []
                for p in day['periods']:
                    serialized_periods.append({
                        "entry_time": p.entry_time.isoformat() if p.entry_time else None,
                        "exit_time": p.exit_time.isoformat() if p.exit_time else None
                    })
                serialized_day['periods'] = serialized_periods
                return serialized_day

        return {"error": "Day not found"}

class PeriodInput(BaseModel):
    entry_time: str = Field(description="Entry time in HH:MM format")
    exit_time: Optional[str] = Field(None, description="Exit time in HH:MM format, or null if currently working")

@mcp.tool()
def update_day_record_tool(
    date_str: str,
    notes: Optional[str] = None,
    manual_holiday: Optional[bool] = None,
    is_consolidated: Optional[bool] = None,
    balance_override: Optional[float] = None,
    periods: Optional[List[PeriodInput]] = None
) -> str:
    """
    Update a specific day record.
    date_str should be YYYY-MM-DD.
    periods is a list of dicts with entry_time and exit_time (HH:MM).
    """
    from datetime import datetime

    target_date = datetime.strptime(date_str, "%Y-%m-%d").date()

    if _flask_app is None:
        raise RuntimeError("Flask app not configured")
    with _flask_app.app_context():
        day_record = DayRecord.query.get(target_date)
        if not day_record:
            return "Error: Day not found"

        if notes is not None:
            day_record.notes = notes
        if manual_holiday is not None:
            day_record.manual_holiday = manual_holiday
        if is_consolidated is not None:
            day_record.is_consolidated = is_consolidated
        if balance_override is not None:
            day_record.balance_override = balance_override

        if periods is not None:
            entries = [p.entry_time for p in periods]
            exits = [p.exit_time for p in periods]
            update_day_periods(day_record, entries, exits)

        try:
            db.session.commit()
            return f"Day {date_str} updated successfully."
        except Exception as e:
            db.session.rollback()
            return f"Error updating day: {str(e)}"
