package com.chm.myfinances.domain.goal;

/**
 * Lifecycle of a {@link Goal} (F039 spec, ADR 0034), set manually. {@code ACHIEVED} is "I decided
 * this is done", separate from the automatic "target reached" indicator (allocated value at least
 * the target), which never changes the status. {@code ARCHIVED} goals stop counting toward a
 * product's 100% and never appear on the dashboard.
 *
 * <p>"Archived goals do not count" is also restated in JPQL by {@code
 * GoalAllocationJpaRepository#sumPercentageByProductExcludingGoal} (checked by {@code
 * GoalRepositoryAdapterTest}'s parity test): change them together.
 */
public enum GoalStatus {
  ACTIVE,
  ACHIEVED,
  ARCHIVED
}
