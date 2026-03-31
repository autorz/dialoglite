from flask import Blueprint, render_template, request, redirect, url_for, flash
from datetime import datetime, date
from .models import db, Settings, DayRecord, TimePeriod
from .core import get_settings, set_start_date, auto_populate_days, get_history_with_balances, update_day_periods

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
        flash('Configurações atualizadas com sucesso!', 'success')
    except Exception as e:
        flash(f'Erro ao atualizar configurações: {e}', 'danger')
    return redirect(url_for('main.index'))

@bp.route('/day/<string:date_str>/quick_update', methods=['POST'])
def day_quick_update(date_str):
    try:
        day_date = datetime.strptime(date_str, '%Y-%m-%d').date()
    except ValueError:
        flash('Formato de data inválido.', 'danger')
        return redirect(url_for('main.index'))

    day_record = DayRecord.query.get_or_404(day_date)

    # Check if there are more than 2 periods already (shouldn't use quick edit)
    if len(day_record.periods) > 2:
        flash('Este dia tem mais de 2 períodos. Por favor, use a edição avançada.', 'warning')
        return redirect(url_for('main.index'))

    # Parse new periods from form
    entries = request.form.getlist('entry_time[]')
    exits = request.form.getlist('exit_time[]')
    update_day_periods(day_record, entries, exits)

    day_record.is_consolidated = True

    try:
        db.session.commit()
        flash(f'Períodos para {day_date.strftime("%d/%m/%Y")} atualizados com sucesso!', 'success')
    except Exception as e:
        db.session.rollback()
        flash(f'Erro ao salvar edição rápida: {e}', 'danger')

    return redirect(url_for('main.index'))

@bp.route('/day/<string:date_str>', methods=['GET', 'POST'])
def day_edit(date_str):
    try:
        day_date = datetime.strptime(date_str, '%Y-%m-%d').date()
    except ValueError:
        flash('Formato de data inválido.', 'danger')
        return redirect(url_for('main.index'))

    day_record = DayRecord.query.get_or_404(day_date)

    if request.method == 'POST':
        # Update Notes and Toggles
        day_record.notes = request.form.get('notes', '')
        day_record.manual_holiday = 'manual_holiday' in request.form
        day_record.is_consolidated = 'is_consolidated' in request.form

        override_val = request.form.get('balance_override')
        if override_val:
            try:
                # Recebemos em float? Não, precisamos parsear um float no formato (ex: 10.5 horas).
                # No entanto o usuario digitará algo no html. Assumimos horas.
                day_record.balance_override = float(override_val)
            except ValueError:
                day_record.balance_override = None
        else:
            day_record.balance_override = None

        # Update Periods
        entries = request.form.getlist('entry_time[]')
        exits = request.form.getlist('exit_time[]')
        update_day_periods(day_record, entries, exits)

        try:
            db.session.commit()
            flash('Dia atualizado com sucesso!', 'success')
            return redirect(url_for('main.index'))
        except Exception as e:
            db.session.rollback()
            flash(f'Erro ao salvar dados: {e}', 'danger')

    return render_template('day_edit.html', day=day_record)
