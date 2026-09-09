import uvicorn
from starlette.applications import Starlette
from starlette.responses import RedirectResponse
from starlette.routing import Mount, Route
from a2wsgi import WSGIMiddleware
from main import create_app
from app.mcp_server import mcp, set_flask_app

flask_app = create_app()
set_flask_app(flask_app)

mcp_app = mcp.http_app(transport="http")


async def _mcp_slash_redirect(request):
    return RedirectResponse(url="/mcp", status_code=307)


app = Starlette(
    lifespan=mcp_app.lifespan,
    routes=[
        *mcp_app.routes,
        Route("/mcp/", endpoint=_mcp_slash_redirect, methods=["GET", "POST", "DELETE"]),
        Mount("/", app=WSGIMiddleware(flask_app)),
    ],
)

if __name__ == "__main__":
    uvicorn.run("asgi:app", host="0.0.0.0", port=8000, reload=True)
