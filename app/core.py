from datetime import date, timedelta, time, datetime
import holidays
from sqlalchemy.orm import joinedload
from .models import db, Settings, DayRecord, TimePeriod

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

def get_history_with_balances():
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
