package com.chm.myfinances.domain.shared;

/**
 * Shared length limits for the {@code description}/{@code additionalNotes} pair of free-text fields
 * used on narrative (not flat-taxonomy) entities — Transaction (F004), and Transfer
 * (F005)/RecurringTemplate (F007) once built — keeping the domain invariant, the DTO {@code @Size}
 * constraint, and the {@code varchar(n)} column all agreeing on the same numbers, same spirit as
 * {@link NameConstraints} for taxonomy "name" fields.
 */
public final class DescriptionConstraints {

  public static final int MAX_DESCRIPTION_LENGTH = 150;
  public static final int MAX_ADDITIONAL_NOTES_LENGTH = 500;

  private DescriptionConstraints() {}
}
