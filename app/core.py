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

def set_start_date(new_start_date: date):
    settings = get_settings()
    settings.start_date = new_start_date
    db.session.commit()
    # Delete any records before the new start date
    DayRecord.query.filter(DayRecord.date < new_start_date).delete()
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
                # Add 09:00 - 12:00
                p1 = TimePeriod(day=new_day, entry_time=time(9, 0), exit_time=time(12, 0))
                # Add 13:00 - 18:00
                p2 = TimePeriod(day=new_day, entry_time=time(13, 0), exit_time=time(18, 0))
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
        # Ex: Expected 8, Worked 10 on Mon -> (10-8)*1 = +2
        # Ex: Expected 0, Worked 4 on Sun -> (4-0)*1.5 = +6
        # Ex: Expected 8, Worked 6 on Tue -> (6-8)*1 = -2
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


def get_dashboard_stats(history: List[dict], current_balance: float) -> dict:
    today = date.today()

    # 1. Gather workdays extras
    # History is descending (newest first)
    last_7_days_deltas = []
    last_30_days_deltas = []
    last_90_days_deltas = []

    last_7_days_arrivals = []
    last_7_days_departures = []
    last_30_days_arrivals = []
    last_30_days_departures = []
    last_90_days_arrivals = []
    last_90_days_departures = []

    for record in history:
        record_date = record['date']
        # Only consider up to today
        if record_date > today:
            continue

        days_ago = (today - record_date).days

        if days_ago < 90:
            if record['periods']:
                entries = [p.entry_time for p in record['periods'] if p.entry_time]
                if entries:
                    first_entry = min(entries)
                    val = first_entry.hour + first_entry.minute / 60.0
                    last_90_days_arrivals.append(val)
                    if days_ago < 30:
                        last_30_days_arrivals.append(val)
                    if days_ago < 7:
                        last_7_days_arrivals.append(val)

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
                    last_90_days_departures.append(max_exit_val)
                    if days_ago < 30:
                        last_30_days_departures.append(max_exit_val)
                        if days_ago < 7:
                            last_7_days_departures.append(max_exit_val)

        # We only consider workdays (not weekend, not holiday)
        if not record['is_weekend'] and not record['is_holiday']:
            if days_ago < 90:
                last_90_days_deltas.append(record['daily_delta'])
                if days_ago < 30:
                    last_30_days_deltas.append(record['daily_delta'])
                    if days_ago < 7:
                        last_7_days_deltas.append(record['daily_delta'])

    def float_to_time_str(val):
        h = int(val)
        m = int(round((val - h) * 60))
        if m == 60:
            h += 1
            m = 0
        h = h % 24
        return f"{h:02d}:{m:02d}"

    avg_7_days_delta = sum(last_7_days_deltas) / len(last_7_days_deltas) if last_7_days_deltas else 0.0
    avg_30_days_delta = sum(last_30_days_deltas) / len(last_30_days_deltas) if last_30_days_deltas else 0.0
    avg_90_days_delta = sum(last_90_days_deltas) / len(last_90_days_deltas) if last_90_days_deltas else 0.0

    avg_7_days_arr_str = float_to_time_str(sum(last_7_days_arrivals) / len(last_7_days_arrivals)) if last_7_days_arrivals else "--:--"
    avg_30_days_arr_str = float_to_time_str(sum(last_30_days_arrivals) / len(last_30_days_arrivals)) if last_30_days_arrivals else "--:--"
    avg_90_days_arr_str = float_to_time_str(sum(last_90_days_arrivals) / len(last_90_days_arrivals)) if last_90_days_arrivals else "--:--"

    avg_7_days_dep_str = float_to_time_str(sum(last_7_days_departures) / len(last_7_days_departures)) if last_7_days_departures else "--:--"
    avg_30_days_dep_str = float_to_time_str(sum(last_30_days_departures) / len(last_30_days_departures)) if last_30_days_departures else "--:--"
    avg_90_days_dep_str = float_to_time_str(sum(last_90_days_departures) / len(last_90_days_departures)) if last_90_days_departures else "--:--"

    # 2. Forecast expected workdays
    future_90_workdays = 0

    for i in range(1, 91):
        future_date = today + timedelta(days=i)

        # Fetch if exists to check manual holiday
        existing = DayRecord.query.get(future_date)
        is_hol = is_holiday(future_date, existing)
        is_weekend = future_date.weekday() >= 5

        if not is_weekend and not is_hol:
            if i <= 90:
                future_90_workdays += 1

    forecast_90_days_7d = (future_90_workdays * avg_7_days_delta)
    forecast_90_days_30d = (future_90_workdays * avg_30_days_delta)
    forecast_90_days_90d = (future_90_workdays * avg_90_days_delta)

    # 3. Chart data (last 90 days, ascending)
    chart_data = []

    # Get last 90 days records (or less if history is shorter)
    # Filter for <= today and take last 90
    past_records = [r for r in history if r['date'] <= today]
    # Since history is newest first, slice top 90 and then reverse to make it ascending
    last_90_records = list(reversed(past_records[:90]))

    # Calculate 7-day moving average
    for i, record in enumerate(last_90_records):
        start_idx = max(0, i - 6)
        window = last_90_records[start_idx:i+1]
        moving_avg = sum(r['balance'] for r in window) / len(window) if window else 0.0

        chart_data.append({
            'date': record['date'].strftime('%Y-%m-%d'),
            'balance': round(record['balance'], 2),
            'moving_avg_7': round(moving_avg, 2)
        })

    return {
        'stats_7d': {
            'arrival_str': avg_7_days_arr_str,
            'departure_str': avg_7_days_dep_str,
            'delta_float': avg_7_days_delta,
            'forecast_90_float': forecast_90_days_7d
        },
        'stats_30d': {
            'arrival_str': avg_30_days_arr_str,
            'departure_str': avg_30_days_dep_str,
            'delta_float': avg_30_days_delta,
            'forecast_90_float': forecast_90_days_30d
        },
        'stats_90d': {
            'arrival_str': avg_90_days_arr_str,
            'departure_str': avg_90_days_dep_str,
            'delta_float': avg_90_days_delta,
            'forecast_90_float': forecast_90_days_90d
        },
        'chart_data': chart_data
    }
