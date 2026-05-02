import os
from flask import Flask
from app.models import db
from app.routes import bp

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

def create_app():
    app = Flask(__name__, template_folder='app/templates', static_folder='app/static')
    
    # Security: Use environment variable or a random key
    app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY') or os.urandom(24).hex()
    
    app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URI', 'sqlite:///app.db')
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

    db.init_app(app)

    with app.app_context():
        # Ensure database tables are created
        db.create_all()
        
        # Migration: Add missing columns to settings table if they don't exist
        try:
            engine = db.engine
            with engine.connect() as conn:
                result = conn.execute(db.text("PRAGMA table_info(settings)"))
                columns = [row[1] for row in result]
                
                if 'default_entry' not in columns:
                    conn.execute(db.text("ALTER TABLE settings ADD COLUMN default_entry TIME NOT NULL DEFAULT '09:00:00'"))
                if 'default_lunch_start' not in columns:
                    conn.execute(db.text("ALTER TABLE settings ADD COLUMN default_lunch_start TIME NOT NULL DEFAULT '12:00:00'"))
                if 'default_lunch_end' not in columns:
                    conn.execute(db.text("ALTER TABLE settings ADD COLUMN default_lunch_end TIME NOT NULL DEFAULT '13:00:00'"))
                if 'default_exit' not in columns:
                    conn.execute(db.text("ALTER TABLE settings ADD COLUMN default_exit TIME NOT NULL DEFAULT '18:00:00'"))
                conn.commit()
        except Exception as e:
            app.logger.error(f"Migration error: {e}")

    app.register_blueprint(bp)

    # Register template filters
    app.jinja_env.filters['format_balance'] = format_balance
    app.jinja_env.filters['format_money'] = format_money
    app.jinja_env.filters['format_time'] = format_time_only
    app.jinja_env.filters['ptbr_weekday'] = ptbr_weekday

    return app

app = create_app()

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=8000)
