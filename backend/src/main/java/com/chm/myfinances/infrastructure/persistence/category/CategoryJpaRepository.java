package com.chm.myfinances.infrastructure.persistence.category;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link CategoryJpaEntity}. Not exposed outside this package. */
interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);
}
