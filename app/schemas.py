from pydantic import BaseModel, Field
from typing import List, Optional
import datetime

class SettingsSchema(BaseModel):
    start_date: datetime.date = Field(..., description="Start date for tracking history")
    default_entry: datetime.time = Field(..., description="Default entry time (HH:MM)")
    default_lunch_start: datetime.time = Field(..., description="Default lunch start time (HH:MM)")
    default_lunch_end: datetime.time = Field(..., description="Default lunch end time (HH:MM)")
    default_exit: datetime.time = Field(..., description="Default exit time (HH:MM)")

class PeriodSchema(BaseModel):
    entry_time: datetime.time = Field(..., description="Time period start")
    exit_time: Optional[datetime.time] = Field(None, description="Time period end, can be null if currently working")

class DayRecordSchema(BaseModel):
    date: datetime.date = Field(..., description="Date of the record")
    is_weekend: bool = Field(..., description="Whether the day is a weekend")
    is_holiday: bool = Field(..., description="Whether the day is a holiday (automatic or manual)")
    worked_hours: float = Field(..., description="Total hours worked")
    worked_hours_pretty: str = Field(..., description="Formatted total hours worked (e.g., '08:30')")
    expected_hours: float = Field(..., description="Expected hours for the day (usually 8 or 0)")
    expected_hours_pretty: str = Field(..., description="Formatted expected hours")
    daily_delta: float = Field(..., description="Difference between worked and expected hours (with multipliers applied)")
    daily_delta_pretty: str = Field(..., description="Formatted daily delta")
    balance: float = Field(..., description="Accumulated balance up to this day")
    balance_pretty: str = Field(..., description="Formatted accumulated balance")
    notes: str = Field("", description="User notes for the day")
    manual_holiday: bool = Field(False, description="Whether the user manually marked this day as a holiday")
    override: Optional[float] = Field(None, description="Manual balance override value")
    override_pretty: Optional[str] = Field(None, description="Formatted manual balance override value")
    is_consolidated: bool = Field(False, description="Whether the user has reviewed and consolidated this day")
    periods: List[PeriodSchema] = Field(default_factory=list, description="List of time periods worked on this day")

class HistoryResponse(BaseModel):
    current_balance: float = Field(..., description="Current accumulated balance")
    current_balance_pretty: str = Field(..., description="Formatted current accumulated balance")
    days: List[DayRecordSchema] = Field(default_factory=list, description="List of day records")

class StatWindowSchema(BaseModel):
    arrival_str: str = Field(..., description="Average arrival time")
    departure_str: str = Field(..., description="Average departure time")
    delta_float: float = Field(..., description="Average daily delta in hours")
    delta_pretty: str = Field(..., description="Formatted average daily delta")
    forecast_90_float: float = Field(..., description="Forecasted balance over next 90 days")
    forecast_90_pretty: str = Field(..., description="Formatted forecasted balance")

class ChartDataPoint(BaseModel):
    date: str = Field(..., description="Date string (YYYY-MM-DD)")
    balance: float = Field(..., description="Balance on this date")

class StatsResponse(BaseModel):
    stats_7d: StatWindowSchema = Field(..., description="Stats for the last 7 days")
    stats_30d: StatWindowSchema = Field(..., description="Stats for the last 30 days")
    stats_90d: StatWindowSchema = Field(..., description="Stats for the last 90 days")
    chart_data: List[ChartDataPoint] = Field(default_factory=list, description="Chart data points for the last 90 days")

class UpdateDayRequest(BaseModel):
    notes: Optional[str] = Field(None, description="User notes for the day")
    manual_holiday: Optional[bool] = Field(None, description="Manually mark day as holiday")
    is_consolidated: Optional[bool] = Field(None, description="Mark day as consolidated")
    balance_override: Optional[float] = Field(None, description="Manual balance override value")
    periods: Optional[List[PeriodSchema]] = Field(None, description="List of time periods worked on this day")

class MessageResponse(BaseModel):
    message: str = Field(..., description="Status message")
