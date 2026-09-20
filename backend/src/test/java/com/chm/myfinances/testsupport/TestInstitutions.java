package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.institution.Institution;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import java.util.UUID;

/**
 * Helper for real-DB tests (F017): every {@code Account} needs a valid {@code institutionId}, and
 * the migration-seeded built-in "No institution" row is always there in the shared Testcontainers
 * Postgres (rollback never removes it), so fixtures point at it instead of each creating their own.
 */
public final class TestInstitutions {

  private TestInstitutions() {}

  /** Id of the built-in institution seeded by {@code V12__institutions.sql}. */
  public static UUID builtInId(InstitutionRepository institutionRepository) {
    return institutionRepository.findAll().stream()
        .filter(Institution::isBuiltIn)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The built-in institution is missing"))
        .getId();
  }
}
