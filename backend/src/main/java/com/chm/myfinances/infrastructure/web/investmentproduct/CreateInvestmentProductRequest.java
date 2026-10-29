package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.chm.myfinances.infrastructure.web.investmentopeningposition.OpeningPositionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code POST /api/investment-products}. {@code accountId} still creates the
 * product's first holding in the same call (F022 spec: a two-write, {@code @Transactional} use
 * case) - the sub-category and notes are optional (some categories, like Crypto, have no natural
 * sub-level, and not every product carries a remark). The optional {@code openingPosition} (F038,
 * ADR 0033) is the holding's starting quantity and average price, written in the same transaction;
 * {@code @Valid} cascades into it only when it is present.
 */
public record CreateInvestmentProductRequest(
    @NotNull UUID accountId,
    @NotNull UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String ticker,
    UUID segmentId,
    @Valid OpeningPositionRequest openingPosition) {}
