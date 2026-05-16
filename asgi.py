import uvicorn
from starlette.applications import Starlette
from a2wsgi import WSGIMiddleware
from main import create_app
from app.mcp_server import mcp, set_flask_app

# Create Flask app
flask_app = create_app()

# Provide Flask app to MCP server for context management
set_flask_app(flask_app)

# FastMCP http_app requires lifespan to be passed to Starlette
# Using "sse" transport creates the /sse and /messages endpoints explicitly
mcp_app = mcp.http_app(transport="sse")

# Create ASGI app
app = Starlette(lifespan=mcp_app.lifespan)

# Mount FastMCP SSE endpoints
app.mount("/mcp", mcp_app)

# Mount Flask app
app.mount("/", WSGIMiddleware(flask_app))

if __name__ == "__main__":
    uvicorn.run("asgi:app", host="0.0.0.0", port=8000, reload=True)
