package com.chm.myfinances.infrastructure.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The app's whole user store (F036, ADR 0029): exactly one user, built from the two configured
 * properties. No JPA entity, no table - this is a lock on the door, not a user model (PRD §4).
 *
 * <p>An unknown username raises {@link UsernameNotFoundException}; {@code
 * DaoAuthenticationProvider} (with its default {@code hideUserNotFoundExceptions}) turns that into
 * the same {@code BadCredentialsException} a wrong password produces, and still burns a
 * password-hash computation, so neither the response nor its timing says which field was wrong.
 */
public class SingleUserDetailsService implements UserDetailsService {

  private final String username;
  private final String passwordHash;

  public SingleUserDetailsService(String username, String passwordHash) {
    this.username = username;
    this.passwordHash = passwordHash;
  }

  @Override
  public UserDetails loadUserByUsername(String requestedUsername) {
    if (requestedUsername == null || !usernameMatches(requestedUsername)) {
      // Deliberately no username in the message (ADR 0011).
      throw new UsernameNotFoundException("Unknown user");
    }
    return User.withUsername(username)
        .password(passwordHash)
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
        .build();
  }

  private boolean usernameMatches(String requestedUsername) {
    return MessageDigest.isEqual(
        username.getBytes(StandardCharsets.UTF_8),
        requestedUsername.getBytes(StandardCharsets.UTF_8));
  }
}
