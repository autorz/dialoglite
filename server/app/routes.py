from flask import Blueprint, render_template, request, redirect, url_for, flash, jsonify
from datetime import datetime, date
from .models import db, Settings, DayRecord, TimePeriod
from .core import get_settings, update_settings_all, auto_populate_days, get_history_with_balances, update_day_periods, get_dashboard_stats

bp = Blueprint('main', __name__)

@bp.route('/')
def index():
    auto_populate_days()
    settings = get_settings()
    history, current_balance = get_history_with_balances()
    stats = get_dashboard_stats(history, current_balance)
    return render_template('index.html', history=history, current_balance=current_balance, settings=settings, stats=stats)

@bp.route('/settings', methods=['POST'])
def update_settings():
    try:
        new_date_str = request.form.get('start_date')
        new_date = datetime.strptime(new_date_str, '%Y-%m-%d').date()

        # Parse new default times
        def_entry = datetime.strptime(request.form.get('default_entry', '09:00'), '%H:%M').time()
        def_lunch_start = datetime.strptime(request.form.get('default_lunch_start', '12:00'), '%H:%M').time()
        def_lunch_end = datetime.strptime(request.form.get('default_lunch_end', '13:00'), '%H:%M').time()
        def_exit = datetime.strptime(request.form.get('default_exit', '18:00'), '%H:%M').time()

        update_settings_all(new_date, def_entry, def_lunch_start, def_lunch_end, def_exit)

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

    entries = request.form.getlist('entry_time[]')
    exits = request.form.getlist('exit_time[]')

    # Time inputs are only rendered when the day has <= 2 periods.
    # When entries is non-empty, the user submitted time edits.
    if entries:
        if len(day_record.periods) > 2:
            flash('Este dia tem mais de 2 períodos. Períodos não foram alterados; use a edição avançada.', 'warning')
        else:
            update_day_periods(day_record, entries, exits)
            day_record.is_consolidated = True

    if 'notes' in request.form:
        day_record.notes = request.form.get('notes', '').strip()

    try:
        db.session.commit()
        flash(f'Dia {day_date.strftime("%d/%m/%Y")} salvo com sucesso!', 'success')
    except Exception as e:
        db.session.rollback()
        flash(f'Erro ao salvar edição rápida: {e}', 'danger')

    return redirect(url_for('main.index'))

@bp.route('/day/bulk_update', methods=['POST'])
def day_bulk_update():
    data = request.get_json(silent=True) or {}
    rows = data.get('rows', [])

    updated = 0
    errors = []

    for r in rows:
        date_str = r.get('date')
        if not date_str:
            continue
        try:
            day_date = datetime.strptime(date_str, '%Y-%m-%d').date()
        except ValueError:
            errors.append({'date': date_str, 'error': 'data inválida'})
            continue

        day_record = DayRecord.query.get(day_date)
        if not day_record:
            errors.append({'date': date_str, 'error': 'dia não encontrado'})
            continue

        entries = r.get('entries')
        if entries is not None:
            if len(day_record.periods) > 2:
                errors.append({'date': date_str, 'error': 'mais de 2 períodos; horários não alterados — use edição avançada'})
            else:
                try:
                    update_day_periods(day_record, entries, r.get('exits', []))
                    if entries:
                        day_record.is_consolidated = True
                except Exception as e:
                    errors.append({'date': date_str, 'error': f'erro nos horários: {e}'})
                    continue

        if 'notes' in r:
            day_record.notes = (r.get('notes') or '').strip()

        updated += 1

    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        return jsonify({'status': 'error', 'message': str(e)}), 500

    return jsonify({'status': 'ok', 'updated': updated, 'errors': errors})


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
