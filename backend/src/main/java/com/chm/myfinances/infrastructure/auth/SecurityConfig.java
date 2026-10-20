package com.chm.myfinances.infrastructure.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Session-cookie authentication for the whole API (F036, ADR 0029): a single shared Argon2
 * credential, a {@code JSESSIONID} cookie, and cookie-based CSRF paired with axios's built-in
 * {@code withXSRFToken}. Required in every profile - there is deliberately no dev bypass, because
 * the dev stack can itself be exposed over a private network.
 *
 * <p>Only {@code POST /api/auth/login} and the Docker healthcheck's {@code /actuator/health} are
 * open; everything else - every controller, plus Swagger UI and {@code /v3/api-docs} where enabled
 * (dev; the {@code prod} profile turns them off) - needs a session. Login/logout/session themselves
 * are ordinary MVC endpoints ({@code AuthController}); this class only wires the filter chain and
 * the beans they share.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return AuthPasswordEncoder.create();
  }

  @Bean
  UserDetailsService userDetailsService(AuthProperties properties) {
    return new SingleUserDetailsService(properties.username(), properties.passwordHash());
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  /** The one repository both the filter chain and {@link SessionAuthenticator} use. */
  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * {@code XSRF-TOKEN} cookie readable by JS ({@code HttpOnly=false}, by design - that is how the
   * SPA echoes it back as {@code X-XSRF-TOKEN}). Not {@code Secure}: TLS is not assumed.
   */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository,
      ProblemDetailAuthenticationEntryPoint entryPoint,
      ProblemDetailAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    // The raw cookie value is what the SPA sends back, so the plain (non-XOR) handler is the match;
    // the token is never rendered into a response body, which is what XOR/BREACH protection is for.
    CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

    http.cors(Customizer.withDefaults())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        // A 401 must not park the request in a session (and mint a JSESSIONID) for a caller who
        // never logs in.
        .requestCache(AbstractHttpConfigurer::disable)
        // The login/logout endpoints are the controller's, not Spring Security's form machinery.
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
