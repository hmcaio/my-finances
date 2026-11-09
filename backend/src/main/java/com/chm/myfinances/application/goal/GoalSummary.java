package com.chm.myfinances.application.goal;

import com.chm.myfinances.domain.goal.Goal;

/**
 * One goal on the dashboard card (F039 spec, ADR 0034): the goal and where it stands ({@link
 * GoalProgress}, from the base scenario).
 */
public record GoalSummary(Goal goal, GoalProgress progress) {}
