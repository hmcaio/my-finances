package com.chm.myfinances.application.investmentreport;

import java.util.List;
import java.util.UUID;

/** The monthly value series of one investment product (F009 spec). */
public record ProductSeries(UUID productId, List<SeriesPoint> points) {}
