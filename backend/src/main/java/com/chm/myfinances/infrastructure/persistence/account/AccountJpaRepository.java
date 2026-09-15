package com.chm.myfinances.infrastructure.persistence.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AccountJpaEntity}. Not exposed outside this package. */
interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {}
