package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.ProjectionPoint;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * One month of a goal's value series (F039): a projection point or a history point. {@code month}
 * is a {@code yyyy-MM} string on the wire (see {@code OpenApiConfig}); {@code value} is in today's
 * money, rounded to cents.
 */
public record GoalPointResponse(YearMonth month, BigDecimal value) {

  static GoalPointResponse from(ProjectionPoint point) {
    return new GoalPointResponse(point.month(), point.value());
  }
}
