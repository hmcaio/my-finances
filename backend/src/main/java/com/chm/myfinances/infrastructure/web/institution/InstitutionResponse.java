package com.chm.myfinances.infrastructure.web.institution;

import com.chm.myfinances.domain.institution.Institution;
import java.util.UUID;

/** API representation of an {@link Institution}; {@code builtIn} marks the "No institution" row. */
public record InstitutionResponse(UUID id, String name, boolean builtIn) {

  public static InstitutionResponse from(Institution institution) {
    return new InstitutionResponse(
        institution.getId(), institution.getName(), institution.isBuiltIn());
  }
}
