package com.chm.myfinances.infrastructure.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Picks the key {@link LoginAttemptTracker} counts failures against (F036, ADR 0029): the socket's
 * remote address, or - only when {@code myfinances.auth.client-ip-header} is configured - that
 * header's value. Prod sets it to nginx's {@code X-Real-IP}: the backend is reachable only through
 * that proxy there, and nginx overwrites the header with the real peer address, so it can't be
 * forged. Everywhere else the header is ignored, so a client can't dodge the limiter by inventing
 * one. A value that doesn't look like an IP address falls back to the remote address, which also
 * keeps arbitrary header text out of the tracker's map.
 *
 * <p>Behind a proxy that doesn't forward the real address (Docker Desktop's port publishing
 * collapses every peer to the gateway address) all clients share one counter - stricter, not
 * weaker.
 */
@Component
public class ClientAddressResolver {

  private static final Pattern IP_ADDRESS = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

  private final String headerName;

  @Autowired
  public ClientAddressResolver(AuthProperties properties) {
    this(properties.clientIpHeader());
  }

  ClientAddressResolver(String headerName) {
    this.headerName = headerName == null || headerName.isBlank() ? null : headerName.trim();
  }

  public String resolve(HttpServletRequest request) {
    if (headerName != null) {
      String value = request.getHeader(headerName);
      if (value != null && IP_ADDRESS.matcher(value.trim()).matches()) {
        return value.trim();
      }
    }
    return request.getRemoteAddr();
  }
}
