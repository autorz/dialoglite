import os
from flask_openapi3 import OpenAPI, Info
from app.models import db
from app.routes import bp
from app.api import api_bp
from app.utils import format_balance, format_time_only, format_money, ptbr_weekday

def create_app():
    info = Info(title="Dialoglite API", version="0.1.0")
    app = OpenAPI(__name__, info=info, template_folder='app/templates', static_folder='app/static')
    
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
    app.register_api(api_bp)

    # Register template filters
    app.jinja_env.filters['format_balance'] = format_balance
    app.jinja_env.filters['format_money'] = format_money
    app.jinja_env.filters['format_time'] = format_time_only
    app.jinja_env.filters['ptbr_weekday'] = ptbr_weekday

    return app

# We leave this for backwards compatibility, but recommend using asgi.py or uvicorn
app = create_app()

if __name__ == '__main__':
    # Fallback to standard Flask runner if executed directly
    app.run(debug=True, host='0.0.0.0', port=8000)
