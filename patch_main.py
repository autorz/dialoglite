import re

with open('main.py', 'r') as f:
    content = f.read()

new_format_balance = """def format_balance(hours_float: float, with_sign=False):
    if hours_float is None:
        return ""

    sign = ""
    if hours_float < 0:
        sign = "-"
    elif hours_float > 0 and with_sign:
        sign = "+"

    val = abs(hours_float)

    # 1 day = 8 hours
    days = int(val // 8)
    remaining_hours_float = val % 8

    hours = int(remaining_hours_float)
    minutes = int(round((remaining_hours_float - hours) * 60))

    # Handle overflow of minutes due to rounding
    if minutes == 60:
        minutes = 0
        hours += 1
        if hours == 8:
            hours = 0
            days += 1

    if days > 0:
        return f"{sign}{days}d {hours:02d}:{minutes:02d}"
    else:
        return f"{sign}{hours:02d}:{minutes:02d}"
"""

# Replace the old format_balance function
content = re.sub(
    r'def format_balance\(hours_float: float\):.*?return f"\{sign\}\{hours:02d\}:\{minutes:02d\}"\n',
    new_format_balance,
    content,
    flags=re.DOTALL
)

with open('main.py', 'w') as f:
    f.write(content)
