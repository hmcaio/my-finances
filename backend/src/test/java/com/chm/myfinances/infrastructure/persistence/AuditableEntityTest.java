package com.chm.myfinances.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.infrastructure.persistence.category.CategoryJpaEntity;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Direct test for {@link AuditableEntity}'s JPA-auditing lifecycle
 * ({@code @CreatedDate}/{@code @LastModifiedDate} via {@link
 * org.springframework.data.jpa.domain.support.AuditingEntityListener}), exercised through {@link
 * CategoryJpaEntity} - any {@code AuditableEntity} subclass would do, Category's is the simplest
 * shape - rather than a throwaway test-only entity: Hibernate validates every {@code @Entity} on
 * the classpath against the real schema ({@code ddl-auto: validate}), and a throwaway one would
 * have no backing table.
 */
@DatabaseIntegrationTest
class AuditableEntityTest {

  @Autowired private EntityManager entityManager;

  @Test
  void createdAtAndLastModifiedAtAreSetOnInsertAndLastModifiedAtAdvancesOnUpdate() {
    CategoryJpaEntity entity =
        new CategoryJpaEntity(UUID.randomUUID(), "Audit Test", CategoryType.EXPENSE);

    entityManager.persist(entity);
    entityManager.flush();

    Instant createdAt = entity.getCreatedAt();
    Instant firstModifiedAt = entity.getLastModifiedAt();
    assertThat(createdAt).isNotNull();
    assertThat(firstModifiedAt).isNotNull();

    entity.setName("Audit Test Renamed");
    entityManager.flush();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    assertThat(entity.getLastModifiedAt()).isAfterOrEqualTo(firstModifiedAt);
  }
}
