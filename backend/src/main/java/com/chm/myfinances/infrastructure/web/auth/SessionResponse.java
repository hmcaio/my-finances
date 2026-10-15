package com.chm.myfinances.infrastructure.web.auth;

/**
 * Body of a successful login and of {@code GET /api/auth/session} (F036): the only thing the
 * frontend needs is that the caller has a session. An unauthenticated caller never gets this body -
 * it gets the {@code 401 unauthenticated} problem instead.
 */
public record SessionResponse(boolean authenticated) {}
