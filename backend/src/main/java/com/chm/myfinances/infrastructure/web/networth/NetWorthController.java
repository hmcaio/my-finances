package com.chm.myfinances.infrastructure.web.networth;

import com.chm.myfinances.application.networth.NetWorthGranularity;
import com.chm.myfinances.application.networth.NetWorthQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for net worth, computed on read (F010 spec, PRD S5.9). */
@RestController
@RequestMapping("/api/net-worth")
public class NetWorthController {

  /** Default trend window when {@code from} is omitted: twelve months up to {@code to}. */
  private static final int DEFAULT_MONTHS = 12;

  private final NetWorthQuery netWorthQuery;
  private final Clock clock;

  public NetWorthController(NetWorthQuery netWorthQuery, Clock clock) {
    this.netWorthQuery = netWorthQuery;
    this.clock = clock;
  }

  /** {@code GET /api/net-worth?asOf=}: {@code asOf} ({@code yyyy-MM-dd}) defaults to today. */
  @GetMapping
  public NetWorthPointResponse netWorth(
      @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf) {
    return NetWorthPointResponse.from(
        netWorthQuery.asOf(asOf != null ? asOf : LocalDate.now(clock)));
  }

  /**
   * {@code GET /api/net-worth/trend?from=&to=&granularity=CHANGE_DATE|MONTH}: {@code from}/{@code
   * to} are {@code yyyy-MM-dd}; {@code to} defaults to today and {@code from} to twelve months
   * before {@code to}; {@code granularity} to {@code CHANGE_DATE}.
   */
  @GetMapping("/trend")
  public List<NetWorthPointResponse> netWorthTrend(
      @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to,
      @RequestParam(name = "granularity", defaultValue = "CHANGE_DATE")
          NetWorthGranularity granularity) {
    LocalDate effectiveTo = to != null ? to : LocalDate.now(clock);
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(DEFAULT_MONTHS);
    return netWorthQuery.trend(effectiveFrom, effectiveTo, granularity).stream()
        .map(NetWorthPointResponse::from)
        .toList();
  }
}
