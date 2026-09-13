package com.myfinances.infrastructure.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial health-check endpoint (F001 scaffolding only) confirming the app boots and is reachable.
 * Used by the frontend's placeholder page to verify frontend-to-backend connectivity, and stands in
 * as the "does the backend respond" target for {@code npm run generate-api-types}.
 *
 * <p>Not a product feature — F002+ add real domain endpoints under their own {@code
 * infrastructure/web/<aggregate>} packages.
 */
@RestController
public class HealthController {

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    return Map.of("status", "UP", "timestamp", Instant.now().toString());
  }
}
