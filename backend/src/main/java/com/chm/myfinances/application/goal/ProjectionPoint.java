package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.time.YearMonth;

/** One month of a projected (or historical) goal value series (F039): money rounded to cents. */
public record ProjectionPoint(YearMonth month, BigDecimal value) {}
