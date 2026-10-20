package com.chm.myfinances.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the single {@link Clock} bean the app reads "now" through (issue #31, B1) - every use
 * case or controller that previously called {@code LocalDate.now()}/{@code YearMonth.now()}
 * directly instead takes a {@link Clock} constructor-injected alongside its other ports, so a test
 * can fix "today" instead of depending on the real wall clock. Defaults to {@link
 * Clock#systemDefaultZone()}, so production behavior is unchanged.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
