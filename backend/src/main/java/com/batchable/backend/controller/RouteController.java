package com.batchable.backend.controller;

// DTOs for request/response payloads
import com.batchable.backend.model.dto.*;

// Service layer that contains business logic
import com.batchable.backend.service.RouteService;

import org.springframework.web.bind.annotation.*; // Spring annotations for REST

import java.util.List;

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/routes")
// Base URL path for all endpoints in this controller
// Example: GET /routes/directions
public class RouteController {

  // Dependency on the service layer
  private final RouteService routeService;

  /**
   * Constructor injection: Spring automatically provides a RouteService instance because it is
   * annotated with @Service
   */
  public RouteController(RouteService routeService) {
    this.routeService = routeService;
  }

  /**
   * Endpoint for computing directions between two points
   *
   * @param from starting location (e.g., address or coordinates)
   * @param to destination location
   * @return DirectionsResponse containing routes, durations, distances
   *
   *         Example request: GET /routes/directions?from=Seattle&to=Portland
   */
  @GetMapping("/directions")
  public DirectionsResponse getDirections(@RequestParam String from, @RequestParam String to) {
    // Delegates to service layer to handle business logic and Google API call
    return routeService.getDirections(from, to);
  }

  /**
   * Endpoint for computing a distance matrix between multiple origins and destinations
   *
   * @param origins list of starting points
   * @param destinations list of endpoints
   * @return DistanceMatrixResponse containing distances and travel times
   *
   *         Example request: GET /routes/distance-matrix?origins=A,B,C&destinations=X,Y,Z
   */
  @GetMapping("/distance-matrix")
  public DistanceMatrixResponse getDistanceMatrix(@RequestParam List<String> origins,
      @RequestParam List<String> destinations) {
    // Delegates to service layer
    return routeService.getDistanceMatrix(origins, destinations);
  }
}
