package com.batchable.backend.integration.route;

import com.batchable.backend.model.dto.DirectDirectionsResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RouteServiceDirectDirectionsIT_CI {

  @Autowired
  private RouteService routeService;

  @Test
  void testGetDirectDirections() {
    String from = "Olympia, WA";
    String to = "Bellingham, WA";

    // Call the service (actual Google API call)
    DirectDirectionsResponse resp = routeService.getDirectDirections(from, to);

    // Assertions for required fields
    assertNotNull(resp, "Response should not be null");
    assertTrue(resp.getDistanceMeters() >= 0, "Distance should be nonnegative");
    assertTrue(resp.getDurationSeconds() >= 0, "Duration should be nonnegative");
  }
}
