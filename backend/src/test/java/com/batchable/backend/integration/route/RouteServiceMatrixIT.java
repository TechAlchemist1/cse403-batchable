package com.batchable.backend.integration.route;

import com.batchable.backend.model.dto.DistanceMatrixResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Integration test for RouteService.getDistanceMatrix.
 *
 * This test makes a real HTTP call to an external distance matrix API (e.g., Google Maps) to verify
 * that the service can successfully fetch travel times between multiple origins and destinations,
 * and that the returned matrix has the correct dimensions and non‑negative values.
 *
 * It uses @SpringBootTest to load the full application context, ensuring that all required beans
 * (including API clients and configuration) are available.
 */
@SpringBootTest
class RouteServiceDistanceMatrixIT {

  @Autowired
  private RouteService routeService;

  /**
   * Tests that a valid request for a distance matrix between two origins and three destinations
   * returns a non‑null response with a matrix of the expected size and non‑negative travel times.
   *
   * This verifies that the external API is reachable, the API key is correctly configured, and that
   * the response mapping works as expected.
   */
  @Test
  void testGetDistanceMatrix() {
    // Origins and destinations for the matrix
    List<String> origins = Arrays.asList("Seattle, WA", "Redmond, WA");
    List<String> destinations = Arrays.asList("Bellevue, WA", "Redmond, WA", "Tacoma, WA");

    // Call the service (actual Google API call)
    DistanceMatrixResponse response = routeService.getDistanceMatrix(origins, destinations);

    // Basic sanity checks
    assertNotNull(response, "Response should not be null");
    int[][] matrix = response.getMatrix();
    assertNotNull(matrix, "Matrix should not be null");
    assertEquals(origins.size(), matrix.length, "Matrix row count should match origins");

    for (int i = 0; i < matrix.length; i++) {
      assertEquals(destinations.size(), matrix[i].length,
          "Matrix column count should match destinations");
      for (int j = 0; j < matrix[i].length; j++) {
        int seconds = matrix[i][j];
        assertTrue(seconds >= 0, "Travel time should be nonnegative");
      }
    }
  }
}
