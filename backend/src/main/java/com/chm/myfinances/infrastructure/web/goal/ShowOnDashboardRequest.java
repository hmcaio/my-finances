package com.chm.myfinances.infrastructure.web.goal;

import jakarta.validation.constraints.NotNull;

/** Request body for {@code PATCH /api/goals/{id}/show-on-dashboard} (F039 spec). */
public record ShowOnDashboardRequest(@NotNull Boolean showOnDashboard) {}
