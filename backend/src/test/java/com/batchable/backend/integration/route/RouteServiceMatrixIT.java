package com.batchable.backend.integration.route;

import com.batchable.backend.model.dto.DistanceMatrixResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
class RouteServiceDistanceMatrixIT {

  @Autowired
  private RouteService routeService;

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
    System.out.println("matrix: " + Arrays.deepToString(matrix));
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
