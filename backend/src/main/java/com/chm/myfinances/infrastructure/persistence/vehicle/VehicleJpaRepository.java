package com.chm.myfinances.infrastructure.persistence.vehicle;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link VehicleJpaEntity}. Not exposed outside this package. */
interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, UUID> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);
}
