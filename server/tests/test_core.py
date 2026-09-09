from datetime import date, time
from app.core import calculate_daily_hours

class MockPeriod:
    def __init__(self, entry_time, exit_time):
        self.entry_time = entry_time
        self.exit_time = exit_time

class MockDayRecord:
    def __init__(self, date, periods):
        self.date = date
        self.periods = periods

def test_calculate_daily_hours_normal():
    day = MockDayRecord(
        date=date(2024, 1, 1),
        periods=[
            MockPeriod(entry_time=time(9, 0), exit_time=time(12, 0))
        ]
    )
    assert calculate_daily_hours(day) == 3.0

def test_calculate_daily_hours_cross_midnight():
    day = MockDayRecord(
        date=date(2024, 1, 1),
        periods=[
            MockPeriod(entry_time=time(23, 0), exit_time=time(2, 0))
        ]
    )
    # 23:00 to 02:00 is 3 hours
    assert calculate_daily_hours(day) == 3.0

def test_calculate_daily_hours_multiple_periods():
    day = MockDayRecord(
        date=date(2024, 1, 1),
        periods=[
            MockPeriod(entry_time=time(9, 0), exit_time=time(12, 0)),
            MockPeriod(entry_time=time(13, 0), exit_time=time(18, 0))
        ]
    )
    assert calculate_daily_hours(day) == 8.0

def test_calculate_daily_hours_null_exit():
    day = MockDayRecord(
        date=date(2024, 1, 1),
        periods=[
            MockPeriod(entry_time=time(9, 0), exit_time=None)
        ]
    )
    assert calculate_daily_hours(day) == 0.0

def test_calculate_daily_hours_mixed():
    day = MockDayRecord(
        date=date(2024, 1, 1),
        periods=[
            MockPeriod(entry_time=time(9, 0), exit_time=time(12, 0)),
            MockPeriod(entry_time=time(22, 0), exit_time=time(1, 0))
        ]
    )
    # 3 hours + 3 hours = 6 hours
    assert calculate_daily_hours(day) == 6.0
