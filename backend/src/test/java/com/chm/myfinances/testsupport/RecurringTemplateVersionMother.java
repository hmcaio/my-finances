package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Test data builder for {@link RecurringTemplateVersion} (issue #31, B6). See {@link
 * RecurringTemplateMother} for the owning template.
 */
public final class RecurringTemplateVersionMother {

  private UUID id = UUID.randomUUID();
  private UUID templateId = UUID.randomUUID();
  private BigDecimal amount = new BigDecimal("100.00");
  private int dayOfMonth = 5;
  private YearMonth effectiveFrom = YearMonth.now();

  private RecurringTemplateVersionMother() {}

  public static RecurringTemplateVersionMother version() {
    return new RecurringTemplateVersionMother();
  }

  public RecurringTemplateVersionMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public RecurringTemplateVersionMother withTemplateId(UUID templateId) {
    this.templateId = templateId;
    return this;
  }

  public RecurringTemplateVersionMother withAmount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  public RecurringTemplateVersionMother withDayOfMonth(int dayOfMonth) {
    this.dayOfMonth = dayOfMonth;
    return this;
  }

  public RecurringTemplateVersionMother withEffectiveFrom(YearMonth effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
    return this;
  }

  public RecurringTemplateVersion build() {
    return RecurringTemplateVersion.create(id, templateId, amount, dayOfMonth, effectiveFrom);
  }
}
