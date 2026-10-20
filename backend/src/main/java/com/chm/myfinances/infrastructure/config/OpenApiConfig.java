package com.chm.myfinances.infrastructure.config;

import java.time.YearMonth;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
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
 *
 * <p>The {@link OperationCustomizer} replaces springdoc's positional operation ids ({@code
 * delete_8}: the Nth method of that name across all controllers) with {@code <controller>_<method>}
 * ({@code account_delete}). Positional ids shift whenever any controller gains a method, and two
 * PRs that each add one regenerate the same id in the frontend's {@code schema.ts} and merge with
 * no conflict, leaving a duplicate identifier that breaks {@code tsc}. Controllers must not
 * overload a handler method name ({@code OpenApiOperationIdsTest} fails on a duplicate).
 */
@Configuration
public class OpenApiConfig {

  static {
    SpringDocUtils.getConfig().replaceWithClass(YearMonth.class, String.class);
  }

  @Bean
  OperationCustomizer stableOperationIds() {
    return (operation, handlerMethod) -> {
      String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
      operation.setOperationId(
          Character.toLowerCase(controller.charAt(0))
              + controller.substring(1)
              + "_"
              + handlerMethod.getMethod().getName());
      return operation;
    };
  }
}
