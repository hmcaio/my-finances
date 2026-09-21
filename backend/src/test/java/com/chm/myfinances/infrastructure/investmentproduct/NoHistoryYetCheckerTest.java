package com.chm.myfinances.infrastructure.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F008's placeholder answers {@code false} for every product until F009 supplies real history. */
class NoHistoryYetCheckerTest {

  @Test
  void neverReportsHistory() {
    assertThat(new NoHistoryYetChecker().hasHistory(UUID.randomUUID())).isFalse();
  }
}
