def format_balance(hours_float: float, with_sign=False):
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

def format_time_only(hours_float: float, with_sign=False):
    if hours_float is None:
        return ""

    sign = ""
    if hours_float < 0:
        sign = "-"
    elif hours_float > 0 and with_sign:
        sign = "+"

    val = abs(hours_float)
    hours = int(val)
    minutes = int(round((val - hours) * 60))

    if minutes == 60:
        minutes = 0
        hours += 1

    return f"{sign}{hours:02d}:{minutes:02d}"


def format_money(hours_float: float, monthly_salary: float):
    if not monthly_salary or monthly_salary <= 0:
        return ""
    if hours_float is None:
        return ""

    hourly_rate = monthly_salary / 220.0

    if hours_float > 0:
        money_value = hours_float * (hourly_rate * 1.5)
    else:
        # hours_float is <= 0
        money_value = hours_float * hourly_rate

    sign = "-" if money_value < 0 else ""
    val = abs(money_value)

    # Format as Brazilian Real (e.g., R$ 1.500,00)
    val_str = f"{val:,.2f}"
    # Replace comma with temporary character, dot with comma, and temporary with dot
    val_str = val_str.replace(",", "X").replace(".", ",").replace("X", ".")

    return f"{sign}R$ {val_str}"

def ptbr_weekday(d):
    weekdays = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]
    return weekdays[d.weekday()]
