package com.chm.myfinances.application.investmentsegment;

import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Use cases for {@link InvestmentSegment}: create/rename/findAll/delete (F026 spec, ADR 0023). New
 * ids come from the {@link IdGenerator} port (ADR 0005). Same shape as {@code
 * InvestmentCategoryService}.
 *
 * <p>Create/rename reject a duplicate name (409, {@link
 * InvestmentSegmentNameAlreadyExistsException}), exact match, case-sensitive. Delete is a 409
 * ({@link InvestmentSegmentInUseException}) while any {@code InvestmentProduct} still references
 * the segment ({@link InvestmentProductRepository#existsBySegmentId}) - same referenced-by-product
 * pattern as {@code InvestmentSubcategoryService}/{@code VehicleService}.
 */
@Service
public class InvestmentSegmentService {

  private final InvestmentSegmentRepository segmentRepository;
  private final InvestmentProductRepository productRepository;
  private final IdGenerator idGenerator;

  public InvestmentSegmentService(
      InvestmentSegmentRepository segmentRepository,
      InvestmentProductRepository productRepository,
      IdGenerator idGenerator) {
    this.segmentRepository = segmentRepository;
    this.productRepository = productRepository;
    this.idGenerator = idGenerator;
  }

  public InvestmentSegment create(String name) {
    if (segmentRepository.existsByName(name)) {
      throw new InvestmentSegmentNameAlreadyExistsException(name);
    }
    InvestmentSegment segment = InvestmentSegment.create(idGenerator.newId(), name);
    return segmentRepository.save(segment);
  }

  public List<InvestmentSegment> findAll() {
    return segmentRepository.findAll();
  }

  public InvestmentSegment findById(UUID id) {
    return segmentRepository
        .findById(id)
        .orElseThrow(() -> new InvestmentSegmentNotFoundException(id));
  }

  public InvestmentSegment rename(UUID id, String newName) {
    InvestmentSegment segment = findById(id);
    if (segmentRepository.existsByNameAndIdNot(newName, id)) {
      throw new InvestmentSegmentNameAlreadyExistsException(newName);
    }
    segment.rename(newName);
    return segmentRepository.save(segment);
  }

  public void delete(UUID id) {
    findById(id);
    if (productRepository.existsBySegmentId(id)) {
      throw new InvestmentSegmentInUseException(id);
    }
    segmentRepository.deleteById(id);
  }
}
