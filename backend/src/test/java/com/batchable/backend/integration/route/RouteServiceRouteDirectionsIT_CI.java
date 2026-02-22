package com.batchable.backend.integration.route;

import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.model.dto.RouteDirectionsResponse.Leg;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Integration test for RouteService.getRouteDirections.
 *
 * This test makes real HTTP calls to an external directions API (e.g., Google Maps) to verify that
 * the service can compute a route from a restaurant address through multiple stops. It tests both
 * the case where detailed leg information is requested and where it is omitted.
 *
 * Uses @SpringBootTest to load the full application context, including API clients and
 * configuration.
 */
@SpringBootTest
class RouteServiceRouteDirectionsIT_CI {

  @Autowired
  private RouteService routeService;

  /**
   * Runs the route test twice: once with leg details disabled, once with them enabled.
   */
  @Test
  void testGetRouteDirections() throws InvalidRouteException {
    String restaurantAddress = "Seattle, WA";
    List<String> stops = Arrays.asList("Bellevue, WA", "Redmond, WA", "Tacoma, WA");

    // Run the same test twice: without legs, then with legs
    for (boolean includeLegs : new boolean[] {false, true}) {
      testRouteForIncludeLegs(restaurantAddress, stops, includeLegs);
    }
  }

  /**
   * Helper that performs the actual route request and validates the response.
   *
   * @param restaurantAddress the starting point
   * @param stops the list of stops (delivery addresses)
   * @param includeLegs whether the API should return detailed leg information
   */
  private void testRouteForIncludeLegs(String restaurantAddress, List<String> stops,
      boolean includeLegs) throws InvalidRouteException {
    RouteDirectionsResponse response =
        routeService.getRouteDirections(restaurantAddress, stops, includeLegs);
    assertNotNull(response, "Response should not be null (includeLegs=" + includeLegs + ")");

    // Common assertions
    String polyline = response.getPolyline();
    int distanceMeters = response.getDistanceMeters();
    int durationSeconds = response.getDurationSeconds();

    assertNotNull(polyline, "Polyline should not be null (includeLegs=" + includeLegs + ")");
    assertFalse(polyline.isEmpty(),
        "Polyline should not be empty (includeLegs=" + includeLegs + ")");
    assertTrue(distanceMeters > 0, "Distance should be positive (includeLegs=" + includeLegs + ")");
    assertTrue(durationSeconds > 0,
        "Duration should be positive (includeLegs=" + includeLegs + ")");

    List<Leg> legs = response.getLegs();
    if (includeLegs) {
      assertNotNull(legs, "Legs should not be null when includeLegs=true");
      assertEquals(1 + stops.size(), legs.size(), "Legs should have size 1 + stops.size()");
      for (Leg leg : legs) {
        assertNotNull(leg, "Leg should not be null when includeLegs=true");
        assertTrue(leg.getDistanceMeters() > 0, "Leg distance should be positive");
      }
    } else {
      assertNull(legs, "Legs should be null when includeLegs=false");
    }
  }
}
