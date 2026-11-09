package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalRepository;

/** In-memory test double for {@link GoalRepository} (F039), shared across application tests. */
public final class FakeGoalRepository extends InMemoryRepository<Goal> implements GoalRepository {

  public FakeGoalRepository() {
    super(Goal::getId);
  }
}
