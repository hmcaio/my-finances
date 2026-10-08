package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.application.investmentsegment.InvestmentSegmentService;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code InvestmentSegment} (F026 spec). */
@RestController
@RequestMapping("/api/investment-segments")
public class InvestmentSegmentController {

  private final InvestmentSegmentService segmentService;

  public InvestmentSegmentController(InvestmentSegmentService segmentService) {
    this.segmentService = segmentService;
  }

  @GetMapping
  public List<InvestmentSegmentResponse> list() {
    return segmentService.findAll().stream().map(InvestmentSegmentResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvestmentSegmentResponse create(@Valid @RequestBody CreateInvestmentSegmentRequest request) {
    InvestmentSegment segment = segmentService.create(request.name());
    return InvestmentSegmentResponse.from(segment);
  }

  @PatchMapping("/{id}")
  public InvestmentSegmentResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentSegmentRequest request) {
    InvestmentSegment segment = segmentService.rename(id, request.name());
    return InvestmentSegmentResponse.from(segment);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    segmentService.delete(id);
  }
}
