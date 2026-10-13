package com.chm.myfinances.infrastructure.web.institution;

import com.chm.myfinances.application.institution.InstitutionService;
import com.chm.myfinances.domain.institution.Institution;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code Institution} (F017 spec). */
@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

  private final InstitutionService institutionService;

  public InstitutionController(InstitutionService institutionService) {
    this.institutionService = institutionService;
  }

  @GetMapping
  public List<InstitutionResponse> list() {
    return institutionService.findAll().stream().map(InstitutionResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:institution-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InstitutionResponse create(@Valid @RequestBody CreateInstitutionRequest request) {
    Institution institution = institutionService.create(request.name());
    return InstitutionResponse.from(institution);
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-blank, size",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:institution-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:institution-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InstitutionResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInstitutionRequest request) {
    Institution institution = institutionService.rename(id, request.name());
    return InstitutionResponse.from(institution);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:institution-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:built-in-institution, "
              + "urn:my-finances:error:institution-in-use",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    institutionService.delete(id);
  }
}
