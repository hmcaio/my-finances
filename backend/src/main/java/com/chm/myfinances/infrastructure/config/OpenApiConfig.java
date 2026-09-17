package com.chm.myfinances.infrastructure.config;

import java.time.YearMonth;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc has a built-in string/{@code format} mapping for {@code LocalDate}/{@code Instant}
 * (swagger-core's default primitive list), but not for {@code YearMonth} - F006's {@code Budget}/
 * {@code BudgetVersion} DTOs are the first place this codebase uses it. Without this, springdoc
 * falls back to reflecting {@code YearMonth}'s own getters ({@code year}/{@code month}/{@code
 * monthValue}/{@code leapYear}) into an object schema, which doesn't match how Jackson actually
 * (de)serializes it on the wire (a plain {@code "yyyy-MM"} string, e.g. {@code "2026-03"}) - and
 * would generate a wrong TypeScript type for the frontend's {@code npm run generate-api-types}.
 * Registering the substitution here maps every {@code YearMonth} field in the generated OpenAPI
 * spec to a plain {@code string} instead.
 */
@Configuration
public class OpenApiConfig {

  static {
    SpringDocUtils.getConfig().replaceWithClass(YearMonth.class, String.class);
  }
}
