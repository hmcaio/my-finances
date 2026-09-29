package com.chm.myfinances.infrastructure.web.vehicle;

import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.application.vehicle.VehicleService;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.infrastructure.web.transaction.TransactionResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code Vehicle} (F024 spec). */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

  private final VehicleService vehicleService;
  private final TransactionService transactionService;

  public VehicleController(VehicleService vehicleService, TransactionService transactionService) {
    this.vehicleService = vehicleService;
    this.transactionService = transactionService;
  }

  @GetMapping
  public List<VehicleResponse> list() {
    return vehicleService.findAll().stream().map(VehicleResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
    Vehicle vehicle = vehicleService.create(request.name());
    return VehicleResponse.from(vehicle);
  }

  @PatchMapping("/{id}")
  public VehicleResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
    Vehicle vehicle = vehicleService.rename(id, request.name());
    return VehicleResponse.from(vehicle);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    vehicleService.delete(id);
  }

  /**
   * Fuel-purchase history for one vehicle, with fuel details and computed ratios, ordered by date -
   * backs the Fuel page's list and charts (F024 spec). {@code from}/{@code to} are optional date
   * bounds; unbounded by default. 404 if {@code id} doesn't resolve to a vehicle.
   */
  @GetMapping("/{id}/fuel-history")
  public List<TransactionResponse> fuelHistory(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    vehicleService.findById(id);
    List<Transaction> history = transactionService.findFuelHistory(id, from, to);
    return history.stream().map(TransactionResponse::from).toList();
  }
}
