package com.chm.myfinances.application.investmentopeningposition;

import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;

/**
 * Result of {@link InvestmentOpeningPositionService#upsert}: the stored position and whether it was
 * newly created ({@code true}) or replaced the holding's existing one ({@code false}) - the REST
 * layer maps that to {@code 201} vs {@code 200}.
 */
public record RecordedOpeningPosition(InvestmentOpeningPosition position, boolean created) {}
