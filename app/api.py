from flask_openapi3 import APIBlueprint
from flask_openapi3.models.tag import Tag
from pydantic import BaseModel, Field
import datetime
from .schemas import (
    SettingsSchema,
    HistoryResponse,
    StatsResponse,
    UpdateDayRequest,
    MessageResponse,
    DayRecordSchema,
    PeriodSchema
)
from .core import (
    get_settings,
    update_settings_all,
    get_history_with_balances,
    get_dashboard_stats,
    update_day_periods,
    auto_populate_days
)
from .utils import format_balance, format_time_only
from .models import db, DayRecord, TimePeriod

api_tag = Tag(name="API", description="Dialoglite Core API")
api_bp = APIBlueprint("api", __name__, url_prefix="/api")

@api_bp.get(
    "/settings",
    tags=[api_tag],
    summary="Get current settings",
    responses={"200": SettingsSchema}
)
def get_settings_api():
    settings = get_settings()
    return SettingsSchema(
        start_date=settings.start_date,
        default_entry=settings.default_entry,
        default_lunch_start=settings.default_lunch_start,
        default_lunch_end=settings.default_lunch_end,
        default_exit=settings.default_exit
    ).model_dump()

@api_bp.put(
    "/settings",
    tags=[api_tag],
    summary="Update settings",
    responses={"200": MessageResponse, "400": MessageResponse}
)
def update_settings_api(body: SettingsSchema):
    try:
        update_settings_all(
            body.start_date,
            body.default_entry,
            body.default_lunch_start,
            body.default_lunch_end,
            body.default_exit
        )
        return MessageResponse(message="Settings updated successfully").model_dump()
    except Exception as e:
        return MessageResponse(message=str(e)).model_dump(), 400

@api_bp.get(
    "/history",
    tags=[api_tag],
    summary="Get history and current balance",
    responses={"200": HistoryResponse}
)
def get_history_api():
    auto_populate_days()
    history, current_balance = get_history_with_balances()

    days = []
    for day in history:
        periods = [
            PeriodSchema(entry_time=p.entry_time, exit_time=p.exit_time)
            for p in day['periods']
        ]
        days.append(DayRecordSchema(
            date=day['date'],
            is_weekend=day['is_weekend'],
            is_holiday=day['is_holiday'],
            worked_hours=day['worked_hours'],
            worked_hours_pretty=format_time_only(day['worked_hours']),
            expected_hours=day['expected_hours'],
            expected_hours_pretty=format_time_only(day['expected_hours']),
            daily_delta=day['daily_delta'],
            daily_delta_pretty=format_balance(day['daily_delta'], with_sign=True),
            balance=day['balance'],
            balance_pretty=format_balance(day['balance'], with_sign=True),
            notes=day['notes'],
            manual_holiday=day['manual_holiday'],
            override=day['override'],
            override_pretty=format_balance(day['override'], with_sign=True) if day['override'] is not None else None,
            is_consolidated=day['is_consolidated'],
            periods=periods
        ))

    return HistoryResponse(
        current_balance=current_balance,
        current_balance_pretty=format_balance(current_balance, with_sign=True),
        days=days
    ).model_dump()

@api_bp.get(
    "/stats",
    tags=[api_tag],
    summary="Get dashboard statistics",
    responses={"200": StatsResponse}
)
def get_stats_api():
    history, current_balance = get_history_with_balances()
    stats = get_dashboard_stats(history, current_balance)

    # Enrich stats dict to match schema by adding pretty formats
    stats['stats_7d']['delta_pretty'] = format_balance(stats['stats_7d']['delta_float'], with_sign=True)
    stats['stats_7d']['forecast_90_pretty'] = format_balance(stats['stats_7d']['forecast_90_float'], with_sign=True)

    stats['stats_30d']['delta_pretty'] = format_balance(stats['stats_30d']['delta_float'], with_sign=True)
    stats['stats_30d']['forecast_90_pretty'] = format_balance(stats['stats_30d']['forecast_90_float'], with_sign=True)

    stats['stats_90d']['delta_pretty'] = format_balance(stats['stats_90d']['delta_float'], with_sign=True)
    stats['stats_90d']['forecast_90_pretty'] = format_balance(stats['stats_90d']['forecast_90_float'], with_sign=True)

    return stats

class DatePathParams(BaseModel):
    date: datetime.date = Field(..., description="Date to update in YYYY-MM-DD format")

@api_bp.get(
    "/days/<string:date>",
    tags=[api_tag],
    summary="Get details of a specific day",
    responses={"200": DayRecordSchema, "404": MessageResponse}
)
def get_day_api(path: DatePathParams):
    day_date = path.date
    auto_populate_days() # Ensure days are created
    history, _ = get_history_with_balances()

    for day in history:
        if day['date'] == day_date:
            periods = [
                PeriodSchema(entry_time=p.entry_time, exit_time=p.exit_time)
                for p in day['periods']
            ]
            return DayRecordSchema(
                date=day['date'],
                is_weekend=day['is_weekend'],
                is_holiday=day['is_holiday'],
                worked_hours=day['worked_hours'],
                worked_hours_pretty=format_time_only(day['worked_hours']),
                expected_hours=day['expected_hours'],
                expected_hours_pretty=format_time_only(day['expected_hours']),
                daily_delta=day['daily_delta'],
                daily_delta_pretty=format_balance(day['daily_delta'], with_sign=True),
                balance=day['balance'],
                balance_pretty=format_balance(day['balance'], with_sign=True),
                notes=day['notes'],
                manual_holiday=day['manual_holiday'],
                override=day['override'],
                override_pretty=format_balance(day['override'], with_sign=True) if day['override'] is not None else None,
                is_consolidated=day['is_consolidated'],
                periods=periods
            ).model_dump()

    return MessageResponse(message="Day not found").model_dump(), 404

@api_bp.put(
    "/days/<string:date>",
    tags=[api_tag],
    summary="Update a specific day",
    responses={"200": MessageResponse, "400": MessageResponse, "404": MessageResponse}
)
def update_day_api(path: DatePathParams, body: UpdateDayRequest):
    day_date = path.date
    day_record = DayRecord.query.get(day_date)

    if not day_record:
        return MessageResponse(message="Day not found").model_dump(), 404

    if body.notes is not None:
        day_record.notes = body.notes
    if body.manual_holiday is not None:
        day_record.manual_holiday = body.manual_holiday
    if body.is_consolidated is not None:
        day_record.is_consolidated = body.is_consolidated
    if body.balance_override is not None:
        day_record.balance_override = body.balance_override

    if body.periods is not None:
        entries = [p.entry_time.strftime('%H:%M') for p in body.periods]
        exits = [p.exit_time.strftime('%H:%M') if p.exit_time else None for p in body.periods]
        update_day_periods(day_record, entries, exits)

    try:
        db.session.commit()
        return MessageResponse(message="Day updated successfully").model_dump()
    except Exception as e:
        db.session.rollback()
        return MessageResponse(message=str(e)).model_dump(), 400
