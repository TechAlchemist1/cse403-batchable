package com.batchable.backend.unit;

import com.batchable.backend.client.GoogleRoutesClient;
import com.batchable.backend.model.dto.DirectionsResponse;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteServiceDirectionsTest {

    @Test
    void testGetDirections() {
        // Arrange
        GoogleRoutesClient mockClient = Mockito.mock(GoogleRoutesClient.class);
        DirectionsResponse expectedResponse = new DirectionsResponse();
        expectedResponse.setDistanceMeters(1000);
        expectedResponse.setDurationSeconds(600);
        Mockito.when(mockClient.getDirections(Mockito.any())).thenReturn(expectedResponse);

        RouteService service = new RouteService(mockClient);

        // Act
        DirectionsResponse actual = service.getDirections("Seattle,WA", "Redmond,WA");

        // Assert
        assertEquals(1000, actual.getDistanceMeters());
        assertEquals(600, actual.getDurationSeconds());
        Mockito.verify(mockClient).getDirections(Mockito.any());
    }
}
