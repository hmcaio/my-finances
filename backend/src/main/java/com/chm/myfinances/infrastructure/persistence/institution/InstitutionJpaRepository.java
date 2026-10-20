package com.chm.myfinances.infrastructure.persistence.institution;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link InstitutionJpaEntity}. Not exposed outside this package. */
interface InstitutionJpaRepository extends JpaRepository<InstitutionJpaEntity, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);
}
