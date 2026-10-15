package com.chm.myfinances.infrastructure.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Dev-only CORS so the Vite dev server (default {@code http://localhost:5173}) can call the Spring
 * Boot API (default {@code localhost:8080}) with the session cookie (F036, ADR 0029).
 *
 * <p>Credentialed CORS needs an exact origin list rather than a {@code localhost:*} wildcard, and
 * an exact list is also the safer shape: any other page on {@code localhost} (another dev server,
 * say) must not be able to make cookie-carrying cross-origin calls to this API. The list comes from
 * {@code myfinances.cors.allowed-origins}, set only by {@code application-dev.yml}; prod leaves it
 * empty and registers no CORS mapping at all - nginx serves the SPA and reverse-proxies {@code
 * /api/} same-origin there (ADR 0006), so there is no cross-origin traffic to allow. CORS remains a
 * dev convenience, not a security boundary: authentication is the gate.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final List<String> allowedOrigins;

  public WebConfig(@Value("${myfinances.cors.allowed-origins:}") List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (allowedOrigins.isEmpty()) {
      return;
    }
    registry
        .addMapping("/**")
        .allowedOrigins(allowedOrigins.toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
  }
}
