package com.chm.myfinances.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * F036: the rate limiter's client key. The proxy header is only honoured when explicitly configured
 * (prod, where only nginx can reach the backend); otherwise - and for anything that doesn't look
 * like an address - it's the socket's remote address, so a client can't dodge the limiter by
 * inventing a header.
 */
class ClientAddressResolverTest {

  private static MockHttpServletRequest requestFrom(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    return request;
  }

  @Test
  void usesTheRemoteAddressWhenNoHeaderIsConfigured() {
    MockHttpServletRequest request = requestFrom("192.168.1.20");
    request.addHeader("X-Real-IP", "6.6.6.6");

    assertThat(new ClientAddressResolver((String) null).resolve(request)).isEqualTo("192.168.1.20");
    assertThat(new ClientAddressResolver("").resolve(request)).isEqualTo("192.168.1.20");
  }

  @Test
  void usesTheConfiguredHeaderWhenPresentAndWellFormed() {
    MockHttpServletRequest request = requestFrom("172.18.0.5");
    request.addHeader("X-Real-IP", "100.64.0.7");

    assertThat(new ClientAddressResolver("X-Real-IP").resolve(request)).isEqualTo("100.64.0.7");
  }

  @Test
  void acceptsAnIpv6HeaderValue() {
    MockHttpServletRequest request = requestFrom("172.18.0.5");
    request.addHeader("X-Real-IP", "fd7a:115c:a1e0::1");

    assertThat(new ClientAddressResolver("X-Real-IP").resolve(request))
        .isEqualTo("fd7a:115c:a1e0::1");
  }

  @Test
  void fallsBackToTheRemoteAddressWhenTheHeaderIsMissing() {
    MockHttpServletRequest request = requestFrom("172.18.0.5");

    assertThat(new ClientAddressResolver("X-Real-IP").resolve(request)).isEqualTo("172.18.0.5");
  }

  @Test
  void fallsBackToTheRemoteAddressWhenTheHeaderIsNotAnAddress() {
    MockHttpServletRequest request = requestFrom("172.18.0.5");
    request.addHeader("X-Real-IP", "not an address\r\ninjected");

    assertThat(new ClientAddressResolver("X-Real-IP").resolve(request)).isEqualTo("172.18.0.5");
  }
}
