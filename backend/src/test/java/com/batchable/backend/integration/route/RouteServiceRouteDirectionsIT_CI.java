package com.batchable.backend.integration.route;

import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.model.dto.RouteDirectionsResponse.Leg;
import com.batchable.backend.service.RouteService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
class RouteServiceRouteDirectionsIT_CI {

  @Autowired
  private RouteService routeService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void testGetRouteDirections() {
    String restaurantAddress = "Seattle, WA";
    List<String> stops = Arrays.asList("Bellevue, WA", "Redmond, WA", "Tacoma, WA");

    // Run the same test twice: without legs, then with legs
    for (boolean includeLegs : new boolean[] {false, true}) {
      testRouteForIncludeLegs(restaurantAddress, stops, includeLegs);
    }
  }

  private void testRouteForIncludeLegs(String restaurantAddress, List<String> stops,
      boolean includeLegs) {
    RouteDirectionsResponse response =
        routeService.getRouteDirections(restaurantAddress, stops, includeLegs);
    assertNotNull(response, "Response should not be null (includeLegs=" + includeLegs + ")");
    
    // useful for debugging
    // try {
    //   // Pretty-print JSON
    //   String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    //   System.out.println("Include legs = " + includeLegs + ":\n" + json);
    // } catch (Exception e) {
    //   e.printStackTrace();
    // }

    // Common assertions
    String polyline = response.getPolyline();
    int distanceMeters = response.getDistanceMeters();
    int durationSeconds = response.getDurationSeconds();

    assertNotNull(polyline, "Polyline should not be null (includeLegs=" + includeLegs + ")");
    assertFalse(polyline.isEmpty(),
        "Polyline should not be empty (includeLegs=" + includeLegs + ")");
    assertTrue(distanceMeters > 0,
        "Distance should be positive (includeLegs=" + includeLegs + ")");
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
