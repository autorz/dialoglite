from flask import Blueprint, render_template, request, redirect, url_for, flash
from datetime import datetime, date
from .models import db, Settings, DayRecord, TimePeriod
from .core import get_settings, set_start_date, auto_populate_days, get_history_with_balances

bp = Blueprint('main', __name__)

@bp.route('/')
def index():
    auto_populate_days()
    settings = get_settings()
    history, current_balance = get_history_with_balances()
    return render_template('index.html', history=history, current_balance=current_balance, settings=settings)

@bp.route('/settings', methods=['POST'])
def update_settings():
    try:
        new_date_str = request.form.get('start_date')
        new_date = datetime.strptime(new_date_str, '%Y-%m-%d').date()
        set_start_date(new_date)
        flash('Settings updated successfully!', 'success')
    except Exception as e:
        flash(f'Error updating settings: {e}', 'danger')
    return redirect(url_for('main.index'))

@bp.route('/day/<string:date_str>', methods=['GET', 'POST'])
def day_edit(date_str):
    try:
        day_date = datetime.strptime(date_str, '%Y-%m-%d').date()
    except ValueError:
        flash('Invalid date format.', 'danger')
        return redirect(url_for('main.index'))

    day_record = DayRecord.query.get_or_404(day_date)

    if request.method == 'POST':
        # Update Notes and Toggles
        day_record.notes = request.form.get('notes', '')
        day_record.manual_holiday = 'manual_holiday' in request.form

        override_val = request.form.get('balance_override')
        if override_val:
            try:
                day_record.balance_override = float(override_val)
            except ValueError:
                day_record.balance_override = None
        else:
            day_record.balance_override = None

        # Update Periods
        # Clear existing periods
        for p in list(day_record.periods):
            db.session.delete(p)

        # Parse new periods from form arrays
        entries = request.form.getlist('entry_time[]')
        exits = request.form.getlist('exit_time[]')

        for entry_str, exit_str in zip(entries, exits):
            if not entry_str:
                continue

            entry_t = datetime.strptime(entry_str, '%H:%M').time()
            exit_t = datetime.strptime(exit_str, '%H:%M').time() if exit_str else None

            new_period = TimePeriod(day=day_record, entry_time=entry_t, exit_time=exit_t)
            db.session.add(new_period)

        try:
            db.session.commit()
            flash('Day updated successfully!', 'success')
            return redirect(url_for('main.index'))
        except Exception as e:
            db.session.rollback()
            flash(f'Error saving data: {e}', 'danger')

    return render_template('day_edit.html', day=day_record)
