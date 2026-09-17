package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.testsupport.FakeRecurringTemplateVersionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link RecurringTemplateCurrentVersionQuery} - a thin wrapper around {@link
 * RecurringTemplateVersion#resolveEffective}, already fully covered by that class's own tests, so
 * this only verifies the wiring: it resolves against "now" and against the right template.
 */
class RecurringTemplateCurrentVersionQueryTest {

  private final FakeRecurringTemplateVersionRepository versionRepository =
      new FakeRecurringTemplateVersionRepository();
  private final RecurringTemplateCurrentVersionQuery query =
      new RecurringTemplateCurrentVersionQuery(versionRepository);

  @Test
  void resolvesTheVersionEffectiveAsOfNow() {
    UUID templateId = UUID.randomUUID();
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.now().minusMonths(1)));

    Optional<RecurringTemplateVersion> current = query.currentVersion(templateId);

    assertThat(current).isPresent();
  }

  @Test
  void returnsEmptyWhenNoVersionIsEffectiveYet() {
    UUID templateId = UUID.randomUUID();
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.now().plusMonths(6)));

    assertThat(query.currentVersion(templateId)).isEmpty();
  }

  @Test
  void ignoresVersionsBelongingToOtherTemplates() {
    UUID templateId = UUID.randomUUID();
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.TEN,
            5,
            YearMonth.now().minusMonths(1)));

    assertThat(query.currentVersion(templateId)).isEmpty();
  }
}
