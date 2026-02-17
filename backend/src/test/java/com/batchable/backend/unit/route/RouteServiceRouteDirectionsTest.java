package com.batchable.backend.unit.route;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.client.GoogleRoutesClient;
import com.batchable.backend.model.TravelMode;
import com.batchable.backend.model.dto.RouteDirectionsRequest;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.service.RouteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class RouteServiceRouteDirectionsTest {

    private GoogleRoutesClient mockClient;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        mockClient = mock(GoogleRoutesClient.class);
        routeService = new RouteService(mockClient);
    }

    @Test
    void testGetRouteDirections() {
        String restaurantAddress = "123 Main St";
        List<String> stops = Arrays.asList("Stop 1", "Stop 2", "Stop 3");

        // Prepare a fake response
        RouteDirectionsResponse fakeResponse = new RouteDirectionsResponse();
        fakeResponse.setPolyline("polyline123");
        fakeResponse.setDistanceMeters(1000);
        fakeResponse.setDurationSeconds(600);

        // When client is called, return fakeResponse
        when(mockClient.getRouteDirections(any(RouteDirectionsRequest.class), eq(false))).thenReturn(fakeResponse);

        // Call the method under test
        RouteDirectionsResponse response = routeService.getRouteDirections(restaurantAddress, stops, false);

        // Verify the client was called with the correct data
        verify(mockClient).getRouteDirections(argThat(req -> 
            req.getOrigin().getAddress().equals(restaurantAddress) &&
            req.getDestination().getAddress().equals(restaurantAddress) &&
            req.getTravelMode() == TravelMode.DRIVE &&
            req.getIntermediates().size() == stops.size() &&
            req.getIntermediates().get(0).getAddress().equals("Stop 1") &&
            req.getIntermediates().get(1).getAddress().equals("Stop 2") &&
            req.getIntermediates().get(2).getAddress().equals("Stop 3")
        ), eq(false));

        // Assert the returned response is what the client returned
        assertEquals("polyline123", response.getPolyline());
        assertEquals(1000, response.getDistanceMeters());
        assertEquals(600, response.getDurationSeconds());
    }
}