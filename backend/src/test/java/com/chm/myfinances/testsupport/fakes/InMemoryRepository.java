package com.chm.myfinances.testsupport.fakes;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Generic map-store CRUD base for the hand-written {@code Fake*Repository} test doubles (issue #31,
 * B14): {@code save}/{@code findById}/{@code findAll}/{@code deleteById}/{@code existsById}, the
 * map-store boilerplate every one of them repeated byte for byte. A {@code Fake*Repository} extends
 * this for the id-keyed aggregate it stores and implements its own domain repository port on top -
 * inheriting whichever of the five common methods that port actually declares (the method
 * signatures line up exactly, so the override is automatic) and adding its own bespoke {@code
 * existsByX}/{@code findByX} queries via {@link #values()}.
 *
 * <p>Not every port declares all five methods (e.g. {@code BudgetRepository} has no delete port),
 * so a subclass simply never exposes the ones its port doesn't declare through the port's own type
 * - the inherited method is still there, but nothing outside the fake ever calls it directly.
 */
public abstract class InMemoryRepository<T> {

  private final Map<UUID, T> store = new HashMap<>();
  private final Function<T, UUID> idExtractor;

  protected InMemoryRepository(Function<T, UUID> idExtractor) {
    this.idExtractor = idExtractor;
  }

  public T save(T entity) {
    store.put(idExtractor.apply(entity), entity);
    return entity;
  }

  public Optional<T> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  public List<T> findAll() {
    return List.copyOf(store.values());
  }

  public void deleteById(UUID id) {
    store.remove(id);
  }

  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  /**
   * The live backing collection, for a subclass's own bespoke {@code existsByX}/{@code findByX}
   * queries (a {@code stream()}/{@code filter}/{@code anyMatch} one-liner) and for {@code
   * removeIf}-style bulk deletes.
   */
  protected final Collection<T> values() {
    return store.values();
  }
}
