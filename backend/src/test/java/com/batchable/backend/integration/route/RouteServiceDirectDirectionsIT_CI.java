package com.batchable.backend.integration.route;

import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.model.dto.DirectDirectionsResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for RouteService.getDirectDirections.
 *
 * This test makes a real HTTP call to an external directions API (e.g., Google Maps) to verify that
 * the service can successfully fetch direct directions between two locations and that the response
 * contains the expected non‑negative values.
 *
 * It uses @SpringBootTest to load the full application context, ensuring that all required beans
 * (including API clients and configuration) are available.
 */
@SpringBootTest
class RouteServiceDirectDirectionsIT_CI {

  @Autowired
  private RouteService routeService;

  /**
   * Tests that a valid request for direct directions between two cities returns a non‑null response
   * with non‑negative distance and duration.
   *
   * This verifies that the external API is reachable, the API key is correctly configured, and that
   * the response mapping works as expected.
   */
  @Test
  void testGetDirectDirections() {
    // Call the service (actual Google API call)
    assertDoesNotThrow(() -> {
      String from = "Olympia, WA";
      String to = "Bellingham, WA";
      DirectDirectionsResponse resp = routeService.getDirectDirections(from, to);
      // Assertions for required fields
      assertNotNull(resp, "Response should not be null");
      assertTrue(resp.getDistanceMeters() >= 0, "Distance should be nonnegative");
      assertTrue(resp.getDurationSeconds() >= 0, "Duration should be nonnegative");
    });

    
    assertThrows(InvalidRouteException.class, () -> {
      String from = "notaplace"; // giberrish
      String to = "Bellingham, WA";
      routeService.getDirectDirections(from, to);
    });

    assertThrows(InvalidRouteException.class, () -> {
      String from = "Olympia, WA"; 
      String to = "afjlkasf"; // giberrish
      routeService.getDirectDirections(from, to);
    });  
  }
}
