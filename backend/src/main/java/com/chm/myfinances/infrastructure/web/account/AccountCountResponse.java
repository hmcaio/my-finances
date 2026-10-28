package com.chm.myfinances.infrastructure.web.account;

/**
 * Response of {@code GET /api/accounts/count}: how many accounts exist, closed ones included. A
 * cheap existence check for the frontend's onboarding gate, with none of the balances the list
 * computes (issue #189).
 */
public record AccountCountResponse(long count) {}
