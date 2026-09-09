from datetime import date, timedelta, time, datetime
import holidays
from sqlalchemy.orm import joinedload
from .models import db, Settings, DayRecord, TimePeriod
from typing import Tuple, List

# Pre-fetch Brazilian holidays for state of SP
br_holidays = holidays.BR(subdiv='SP', years=range(2020, 2040))

def get_settings():
    settings = Settings.query.first()
    if not settings:
        settings = Settings(start_date=date(2026, 1, 1))
        db.session.add(settings)
        db.session.commit()
    return settings

def update_settings_all(new_start_date: date, entry: time, lunch_start: time, lunch_end: time, exit: time):
    settings = get_settings()
    
    old_start_date = settings.start_date
    
    settings.start_date = new_start_date
    settings.default_entry = entry
    settings.default_lunch_start = lunch_start
    settings.default_lunch_end = lunch_end
    settings.default_exit = exit
    
    db.session.commit()
    
    # If start date changed, delete old records
    if new_start_date > old_start_date:
        DayRecord.query.filter(DayRecord.date < new_start_date).delete()
        db.session.commit()
    
    # Update today's record if not consolidated
    update_today_defaults(settings)

def update_today_defaults(settings):
    today = date.today()
    day = DayRecord.query.get(today)
    if day and not day.is_consolidated:
        # Clear existing periods
        for p in list(day.periods):
            db.session.delete(p)
        
        p1 = TimePeriod(day=day, entry_time=settings.default_entry, exit_time=settings.default_lunch_start)
        p2 = TimePeriod(day=day, entry_time=settings.default_lunch_end, exit_time=settings.default_exit)
        db.session.add(p1)
        db.session.add(p2)
        db.session.commit()

def is_holiday(check_date: date, day_record: DayRecord) -> bool:
    if day_record and day_record.manual_holiday:
        return True
    return check_date in br_holidays

def auto_populate_days():
    """
    Ensure all days from start_date to today are created in the database.
    Auto-fills periods for normal working days.
    """
    settings = get_settings()
    start_date = settings.start_date
    today = date.today()

    # In case testing date is behind start_date, don't crash
    if today < start_date:
        return

    current_date = start_date
    records_added = False

    while current_date <= today:
        existing = DayRecord.query.get(current_date)
        if not existing:
            # Create day record
            new_day = DayRecord(date=current_date)
            db.session.add(new_day)

            # Auto populate periods if it's Mon-Fri and not a holiday
            # weekday(): Mon=0, Sun=6
            if current_date.weekday() < 5 and not is_holiday(current_date, new_day):
                p1 = TimePeriod(day=new_day, entry_time=settings.default_entry, exit_time=settings.default_lunch_start)
                p2 = TimePeriod(day=new_day, entry_time=settings.default_lunch_end, exit_time=settings.default_exit)
                db.session.add(p1)
                db.session.add(p2)

            records_added = True

        current_date += timedelta(days=1)

    if records_added:
        db.session.commit()

def update_day_periods(day_record: DayRecord, entries: list, exits: list):
    """
    Clears existing periods for a day record and adds new ones from entries and exits lists.
    """
    # Clear existing periods
    for p in list(day_record.periods):
        db.session.delete(p)

    # Parse new periods from form arrays
    for entry_str, exit_str in zip(entries, exits):
        if not entry_str:
            continue

        entry_t = datetime.strptime(entry_str, '%H:%M').time()
        exit_t = datetime.strptime(exit_str, '%H:%M').time() if exit_str else None

        new_period = TimePeriod(day=day_record, entry_time=entry_t, exit_time=exit_t)
        db.session.add(new_period)

def calculate_daily_hours(day_record: DayRecord) -> float:
    total_hours = 0.0
    for period in day_record.periods:
        if period.exit_time:
            # Calculate duration in hours
            entry_dt = datetime.combine(day_record.date, period.entry_time)

            # Handle cross-midnight if exit is "earlier" than entry (e.g. entry 23:00, exit 02:00)
            if period.exit_time < period.entry_time:
                 exit_dt = datetime.combine(day_record.date + timedelta(days=1), period.exit_time)
            else:
                 exit_dt = datetime.combine(day_record.date, period.exit_time)

            duration = (exit_dt - entry_dt).total_seconds() / 3600.0
            total_hours += duration
    return total_hours

def get_history_with_balances() -> Tuple[List[dict], float]:
    """
    Returns a list of day dictionaries with calculated overtime balance.
    Sorted descending by date for display, but calculated ascending.
    """
    settings = get_settings()

    # Fetch all days ordered ascending to calculate running balance
    days = DayRecord.query.options(joinedload(DayRecord.periods)).order_by(DayRecord.date.asc()).all()

    running_balance = 0.0
    history = []

    for day in days:
        worked_hours = calculate_daily_hours(day)

        # Determine expected hours and multiplier
        is_weekend = day.date.weekday() >= 5
        is_sun = day.date.weekday() == 6
        is_hol = is_holiday(day.date, day)

        if is_weekend or is_hol:
            expected_hours = 0.0
        else:
            expected_hours = 8.0

        multiplier = 1.5 if (is_sun or is_hol) else 1.0

        # Calculate daily delta
        daily_delta = (worked_hours - expected_hours) * multiplier

        running_balance += daily_delta

        # Override takes precedence if set
        if day.balance_override is not None:
            running_balance = day.balance_override

        # Append info for this day
        history.append({
            'date': day.date,
            'is_weekend': is_weekend,
            'is_holiday': is_hol,
            'worked_hours': worked_hours,
            'expected_hours': expected_hours,
            'daily_delta': daily_delta,
            'balance': running_balance,
            'notes': day.notes,
            'manual_holiday': day.manual_holiday,
            'override': day.balance_override,
            'is_consolidated': day.is_consolidated,
            'periods': day.periods
        })

    # Reverse for frontend (newest first)
    history.reverse()

    return history, running_balance


def _float_to_time_str(val):
    h = int(val)
    m = int(round((val - h) * 60))
    if m == 60:
        h += 1
        m = 0
    h = h % 24
    return f"{h:02d}:{m:02d}"


def _count_future_workdays(today: date, horizon_days: int = 90) -> int:
    n = 0
    for i in range(1, horizon_days + 1):
        future_date = today + timedelta(days=i)
        existing = DayRecord.query.get(future_date)
        is_hol = is_holiday(future_date, existing)
        is_weekend = future_date.weekday() >= 5
        if not is_weekend and not is_hol:
            n += 1
    return n


def _window_stats(records: list, future_90_workdays: int) -> dict:
    """
    Given a list of history records already filtered to a date window, return
    {arrival_str, departure_str, delta_float, forecast_90_float}.

    Arrival/departure averages: any day with at least one period.
    Daily delta average: workdays only (not weekend, not holiday).
    Projection: avg_delta * future_90_workdays.
    """
    arrivals = []
    departures = []
    deltas = []

    for record in records:
        if record['periods']:
            entries = [p.entry_time for p in record['periods'] if p.entry_time]
            if entries:
                first_entry = min(entries)
                arrivals.append(first_entry.hour + first_entry.minute / 60.0)

            max_exit_val = 0.0
            for p in record['periods']:
                if not p.entry_time or not p.exit_time:
                    continue
                val = p.exit_time.hour + p.exit_time.minute / 60.0
                if p.exit_time < p.entry_time:
                    val += 24.0
                if val > max_exit_val:
                    max_exit_val = val
            if max_exit_val > 0:
                departures.append(max_exit_val)

        if not record['is_weekend'] and not record['is_holiday']:
            deltas.append(record['daily_delta'])

    avg_delta = sum(deltas) / len(deltas) if deltas else 0.0
    avg_arr_str = _float_to_time_str(sum(arrivals) / len(arrivals)) if arrivals else "--:--"
    avg_dep_str = _float_to_time_str(sum(departures) / len(departures)) if departures else "--:--"

    return {
        'arrival_str': avg_arr_str,
        'departure_str': avg_dep_str,
        'delta_float': avg_delta,
        'forecast_90_float': future_90_workdays * avg_delta,
    }


def get_dashboard_stats(history: List[dict], current_balance: float) -> dict:
    today = date.today()
    future_90_workdays = _count_future_workdays(today)

    def slice_last(n):
        return [r for r in history if r['date'] <= today and (today - r['date']).days < n]

    stats_7d = _window_stats(slice_last(7), future_90_workdays)
    stats_30d = _window_stats(slice_last(30), future_90_workdays)
    stats_90d = _window_stats(slice_last(90), future_90_workdays)

    # Chart data (last 90 days, ascending)
    past_records = [r for r in history if r['date'] <= today]
    last_90_records = list(reversed(past_records[:90]))
    chart_data = [{'date': r['date'].strftime('%Y-%m-%d'), 'balance': round(r['balance'], 2)} for r in last_90_records]

    return {
        'stats_7d': stats_7d,
        'stats_30d': stats_30d,
        'stats_90d': stats_90d,
        'chart_data': chart_data,
    }


def get_custom_range_stats(history: List[dict], start_date: date, end_date: date) -> dict:
    """
    Stats bounded by an explicit [start_date, end_date] inclusive range. Replaces
    the fixed 'Projeção 90d' card with `period_balance` — the accumulated change
    in saldo within the range, computed as balance at end minus balance just
    before start. Using the running balance (instead of summing deltas) preserves
    the semantics of `balance_override`, which resets the running balance.
    """
    if end_date < start_date:
        raise ValueError("end_date must be on or after start_date")

    records = [r for r in history if start_date <= r['date'] <= end_date]
    stats = _window_stats(records, 0)
    stats.pop('forecast_90_float', None)

    sorted_asc = sorted(history, key=lambda r: r['date'])
    pre_start = None
    end_record = None
    in_range = []
    for r in sorted_asc:
        if r['date'] < start_date:
            pre_start = r
        elif r['date'] <= end_date:
            end_record = r
            in_range.append(r)
        else:
            break

    balance_pre = pre_start['balance'] if pre_start else 0.0
    balance_end = end_record['balance'] if end_record else balance_pre
    stats['period_balance_float'] = balance_end - balance_pre

    stats['chart_data'] = [
        {'date': r['date'].strftime('%Y-%m-%d'), 'balance': round(r['balance'], 2)}
        for r in in_range
    ]
    stats['start_date'] = start_date
    stats['end_date'] = end_date
    stats['days_in_range'] = (end_date - start_date).days + 1
    return stats
