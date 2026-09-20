package com.chm.myfinances.infrastructure.web.institution;

import com.chm.myfinances.application.institution.InstitutionService;
import com.chm.myfinances.domain.institution.Institution;
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
  public InstitutionResponse create(@Valid @RequestBody CreateInstitutionRequest request) {
    Institution institution = institutionService.create(request.name());
    return InstitutionResponse.from(institution);
  }

  @PatchMapping("/{id}")
  public InstitutionResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInstitutionRequest request) {
    Institution institution = institutionService.rename(id, request.name());
    return InstitutionResponse.from(institution);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    institutionService.delete(id);
  }
}
