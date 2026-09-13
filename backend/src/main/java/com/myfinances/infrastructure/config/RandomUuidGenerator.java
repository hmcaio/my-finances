package com.myfinances.infrastructure.config;

import com.myfinances.domain.shared.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The only {@link IdGenerator} implementation (see ADR 0005): backed by {@link UUID#randomUUID()}.
 */
@Component
public class RandomUuidGenerator implements IdGenerator {

  @Override
  public UUID newId() {
    return UUID.randomUUID();
  }
}
