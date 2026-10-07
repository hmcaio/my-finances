package com.chm.myfinances.infrastructure.persistence.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/** Spring Data JPA interface projection for a {@code GROUP BY categoryId} sum query. */
interface CategoryTotalProjection {

  UUID getCategoryId();

  BigDecimal getTotal();
}
