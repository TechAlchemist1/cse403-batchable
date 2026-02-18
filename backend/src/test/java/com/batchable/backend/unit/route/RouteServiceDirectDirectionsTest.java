package com.batchable.backend.unit.route;

import com.batchable.backend.client.GoogleRoutesClient;
import com.batchable.backend.model.dto.DirectDirectionsResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for RouteService using a mocked GoogleRoutesClient.
 *
 * Verifies that RouteService correctly delegates to the client and returns the expected response
 * without making real HTTP calls.
 */
class RouteServiceDirectDirectionsTest {

  /**
   * Tests that getDirectDirections calls the underlying client and returns the mocked distance and
   * duration values.
   */
  @Test
  void testGetDirections() {
    // Arrange
    GoogleRoutesClient mockClient = Mockito.mock(GoogleRoutesClient.class);
    DirectDirectionsResponse expectedResponse = new DirectDirectionsResponse();
    expectedResponse.setDistanceMeters(1000);
    expectedResponse.setDurationSeconds(600);
    Mockito.when(mockClient.getDirectDirections(Mockito.any())).thenReturn(expectedResponse);

    RouteService service = new RouteService(mockClient);

    // Act
    DirectDirectionsResponse actual = service.getDirectDirections("Seattle,WA", "Redmond,WA");

    // Assert
    assertEquals(1000, actual.getDistanceMeters());
    assertEquals(600, actual.getDurationSeconds());
    Mockito.verify(mockClient).getDirectDirections(Mockito.any());
  }
}
