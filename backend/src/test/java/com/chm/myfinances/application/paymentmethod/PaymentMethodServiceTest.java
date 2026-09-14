package com.chm.myfinances.application.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link PaymentMethodService}, against hand-written fakes — plain
 * JUnit, no Spring context (ADR 0004). Per F002 plan.md, the referenced-by-transaction delete guard
 * is deferred to F004; only unconditional create/rename/delete are covered here.
 */
class PaymentMethodServiceTest {

  private final FakePaymentMethodRepository repository = new FakePaymentMethodRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final PaymentMethodService service = new PaymentMethodService(repository, idGenerator);

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    idGenerator.nextId = nextId;

    PaymentMethod created = service.create("Debit Card");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Debit Card");
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void findAllReturnsEveryPersistedPaymentMethod() {
    service.create("Debit Card");
    service.create("Cash");

    List<PaymentMethod> all = service.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .containsExactlyInAnyOrder("Debit Card", "Cash");
  }

  @Test
  void renameUpdatesTheName() {
    PaymentMethod created = service.create("Debit Card");

    PaymentMethod renamed = service.rename(created.getId(), "Debit Card (Itau)");

    assertThat(renamed.getName()).isEqualTo("Debit Card (Itau)");
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "New name"))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }

  @Test
  void deleteRemovesThePaymentMethod() {
    PaymentMethod created = service.create("Temp");

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }

  private static final class FakeIdGenerator implements IdGenerator {
    // null unless a test pins the next id to assert on it; otherwise generates a fresh one per
    // call, since a fixed value would make every create() collide on the same key.
    private UUID nextId;

    @Override
    public UUID newId() {
      return nextId != null ? nextId : UUID.randomUUID();
    }
  }

  private static final class FakePaymentMethodRepository implements PaymentMethodRepository {
    private final Map<UUID, PaymentMethod> store = new HashMap<>();

    @Override
    public PaymentMethod save(PaymentMethod paymentMethod) {
      store.put(paymentMethod.getId(), paymentMethod);
      return paymentMethod;
    }

    @Override
    public Optional<PaymentMethod> findById(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PaymentMethod> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public void deleteById(UUID id) {
      store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
      return store.containsKey(id);
    }
  }
}
