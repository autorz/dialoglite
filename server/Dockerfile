FROM python:3.12-slim

# Install uv
COPY --from=ghcr.io/astral-sh/uv:0.5.11 /uv /uvx /bin/

WORKDIR /app

# Copy the dependencies map
COPY pyproject.toml uv.lock ./

# Install dependencies (this will create a .venv in /app)
ENV UV_COMPILE_BYTECODE=1
RUN uv sync --frozen --no-install-project --no-dev

# Copy the application code
COPY . /app

# Final environment variables
ENV PATH="/app/.venv/bin:$PATH"
ENV FLASK_APP=main.py
ENV FLASK_ENV=production

# Make sure data directory exists
RUN mkdir -p /app/data

# Run the app
CMD ["uvicorn", "asgi:app", "--host", "0.0.0.0", "--port", "8000"]
