import os
from flask import Flask
from app.models import db
from app.routes import bp

def format_balance(hours_float: float):
    if hours_float is None:
        return ""

    sign = "-" if hours_float < 0 else ""
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

def ptbr_weekday(d):
    weekdays = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"]
    return weekdays[d.weekday()]

def create_app():
    app = Flask(__name__, template_folder='app/templates', static_folder='app/static')
    app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'dev-key-fallback')
    app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URI', 'sqlite:///app.db')
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

    db.init_app(app)

    with app.app_context():
        # Ensure database tables are created
        db.create_all()

    app.register_blueprint(bp)

    # Register template filters
    app.jinja_env.filters['format_balance'] = format_balance
    app.jinja_env.filters['format_time'] = format_time_only
    app.jinja_env.filters['ptbr_weekday'] = ptbr_weekday

    return app

app = create_app()

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8000)
