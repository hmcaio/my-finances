package com.chm.myfinances.infrastructure.web.allocationplan;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the FII {@code AllocationPlan}/{@code AllocationPlanVersion} (F026 spec, ADR 0023).
 * {@code GET .../allocation-plan} resolves the version effective for the current real-world month,
 * mirroring {@code BudgetController}'s own-month resolution - {@code 204 No Content} (not a
 * body-carrying 404) while no allocation has ever been set, since that is this feature's normal
 * starting state, not an error.
 */
@RestController
@RequestMapping("/api/fii/allocation-plan")
public class AllocationPlanController {

  private final AllocationPlanService allocationPlanService;
  private final Clock clock;

  public AllocationPlanController(AllocationPlanService allocationPlanService, Clock clock) {
    this.allocationPlanService = allocationPlanService;
    this.clock = clock;
  }

  @GetMapping
  public ResponseEntity<AllocationPlanVersionResponse> current() {
    return allocationPlanService
        .getCurrent(YearMonth.now(clock))
        .map(version -> ResponseEntity.ok(AllocationPlanVersionResponse.from(version)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /** Every version ever set, oldest first - the plan's own history (F026 spec). */
  @GetMapping("/versions")
  public List<AllocationPlanVersionResponse> versions() {
    return allocationPlanService.findVersions().stream()
        .sorted(Comparator.comparing(AllocationPlanVersion::getEffectiveFrom))
        .map(AllocationPlanVersionResponse::from)
        .toList();
  }

  @PutMapping
  public AllocationPlanVersionResponse setAllocation(
      @Valid @RequestBody SetAllocationPlanRequest request) {
    List<AllocationPlanEntry> entries =
        request.entries().stream()
            .map(e -> new AllocationPlanEntry(e.investmentProductId(), e.targetPercentage()))
            .toList();
    AllocationPlanVersion version =
        allocationPlanService.setAllocation(entries, request.effectiveFrom());
    return AllocationPlanVersionResponse.from(version);
  }
}
