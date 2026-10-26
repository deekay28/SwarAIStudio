from __future__ import annotations

import hashlib
import hmac

from fastapi import Header, HTTPException, Request, status


def require_token(request: Request, authorization: str = Header(default="")) -> str:
    """Returns a stable, non-reversible owner id for the presented token."""
    tokens: list[str] = request.app.state.settings.app_tokens
    if not tokens:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Server has no SWARAI_APP_TOKENS configured.")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing bearer token.",
                            headers={"WWW-Authenticate": "Bearer"})
    if not any(hmac.compare_digest(token.encode(), t.encode()) for t in tokens):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token.", headers={"WWW-Authenticate": "Bearer"})
    return hashlib.sha256(token.encode()).hexdigest()[:24]
