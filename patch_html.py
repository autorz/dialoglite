import re

with open('app/templates/index.html', 'r') as f:
    content = f.read()

# Replace Delta Diário cell
old_delta = """<td class="{{ 'text-success fw-bold' if record.daily_delta > 0 else 'text-danger fw-bold' if record.daily_delta < 0 else '' }}">
                    {{ record.daily_delta | format_time(True) }}
                </td>"""
new_delta = """<td class="daily-delta-cell fw-bold" data-value="{{ record.daily_delta }}" data-date="{{ record.date.strftime('%Y-%m-%d') }}">
                    {{ record.daily_delta | format_time(True) }}
                </td>"""
content = content.replace(old_delta, new_delta)

# Replace Saldo Acumulado cell
old_balance = """<td>
                    {% if record.override is not none %}
                        <span class="badge bg-info text-dark" title="Ajuste Manual">
                            {{ record.balance | format_balance }} *
                        </span>
                    {% else %}
                        <strong>{{ record.balance | format_balance }}</strong>
                    {% endif %}
                </td>"""
new_balance = """<td class="accumulated-balance-cell fw-bold" data-value="{{ record.balance }}" data-date="{{ record.date.strftime('%Y-%m-%d') }}">
                    {% if record.override is not none %}
                        <span class="badge bg-info text-dark" title="Ajuste Manual" style="color: inherit !important;">
                            {{ record.balance | format_balance }} *
                        </span>
                    {% else %}
                        <span>{{ record.balance | format_balance }}</span>
                    {% endif %}
                </td>"""
content = content.replace(old_balance, new_balance)

with open('app/templates/index.html', 'w') as f:
    f.write(content)
