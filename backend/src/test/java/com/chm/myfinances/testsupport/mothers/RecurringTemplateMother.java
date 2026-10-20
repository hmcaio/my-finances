package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import java.util.UUID;

/**
 * Test data builder for {@link RecurringTemplate} (issue #31, B6): a valid, in-memory template with
 * sensible defaults that a test overrides only where it cares. See {@link
 * RecurringTemplateVersionMother} for its version. A test asserting on {@link
 * RecurringTemplate#create}'s own validation should keep calling {@code
 * RecurringTemplate.create(...)} directly - going through this builder would obscure what's being
 * tested.
 */
public final class RecurringTemplateMother {

  private UUID id = UUID.randomUUID();
  private UUID categoryId = UUID.randomUUID();
  private UUID accountId = UUID.randomUUID();
  private String description = "Rent";

  private RecurringTemplateMother() {}

  public static RecurringTemplateMother template() {
    return new RecurringTemplateMother();
  }

  public RecurringTemplateMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public RecurringTemplateMother withCategoryId(UUID categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  public RecurringTemplateMother withAccountId(UUID accountId) {
    this.accountId = accountId;
    return this;
  }

  public RecurringTemplateMother withDescription(String description) {
    this.description = description;
    return this;
  }

  public RecurringTemplate build() {
    return RecurringTemplate.create(id, categoryId, accountId, description);
  }
}
