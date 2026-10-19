package com.chm.myfinances.infrastructure.persistence.paymentmethod;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link PaymentMethodJpaEntity}. Not exposed outside this package. */
interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodJpaEntity, UUID> {}
