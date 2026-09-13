package com.myfinances.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Permissive-for-localhost CORS so the Vite dev server (default {@code localhost:5173}) can call
 * the Spring Boot API (default {@code localhost:8080}) in local dev. No auth is added anywhere in
 * this project (PRD S3, S7.1) — this is a dev convenience only, not a security boundary.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns("http://localhost:*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }
}
