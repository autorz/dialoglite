from flask_sqlalchemy import SQLAlchemy
from datetime import date, time

db = SQLAlchemy()

class Settings(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    start_date = db.Column(db.Date, nullable=False, default=date(2026, 1, 1))

class DayRecord(db.Model):
    date = db.Column(db.Date, primary_key=True)
    notes = db.Column(db.Text, default="")
    manual_holiday = db.Column(db.Boolean, default=False)
    balance_override = db.Column(db.Float, nullable=True)

    # Use backref to easily get periods from day
    periods = db.relationship('TimePeriod', backref='day', lazy=True, cascade="all, delete-orphan", order_by="TimePeriod.entry_time")

class TimePeriod(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    day_date = db.Column(db.Date, db.ForeignKey('day_record.date'), nullable=False)
    entry_time = db.Column(db.Time, nullable=False)
    exit_time = db.Column(db.Time, nullable=True) # Can be null if currently working
