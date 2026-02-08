package com.batchable.backend.service;

// Client responsible for talking to Google Routes API
import com.batchable.backend.client.GoogleRoutesClient;

// DTOs used to build requests and return responses
import com.batchable.backend.model.dto.*;

// Marks this class as a Spring service (business logic layer)
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteService {

    // Dependency on the client that actually calls Google
    // This keeps HTTP logic out of controllers
    private final GoogleRoutesClient routesClient;

    /**
     * Constructor injection:
     *
     * Spring automatically provides a GoogleRoutesClient
     * because it is annotated with @Service.
     */
    public RouteService(GoogleRoutesClient routesClient) {
        this.routesClient = routesClient;
    }

    /**
     * Business-level method for getting directions
     * between two locations.
     *
     * This method:
     * - translates simple inputs (Strings)
     * - into a structured DirectionsRequest DTO
     * - sets default business rules (e.g., travel mode)
     * - delegates the API call to the client
     */
    public DirectionsResponse getDirections(String from, String to) {

        // Build the request object expected by Google Routes
        DirectionsRequest req = new DirectionsRequest();
        req.setOrigin(from);
        req.setDestination(to);

        // Business decision:
        // We currently only support driving directions
        req.setTravelMode("DRIVE");

        // Delegate the external API call to the client
        return routesClient.getDirections(req);
    }

    /**
     * Business-level method for computing a distance matrix
     * between multiple origins and destinations.
     *
     * This method hides Google-specific request details
     * from the controller.
     */
    public DistanceMatrixResponse getDistanceMatrix(
            List<String> origins,
            List<String> destinations
    ) {
        // Build the request DTO expected by Google
        DistanceMatrixRequest req = new DistanceMatrixRequest();
        req.setOrigins(origins);
        req.setDestinations(destinations);

        // Centralized business rule for travel mode
        req.setTravelMode("DRIVE");

        // Delegate the API call
        return routesClient.getDistanceMatrix(req);
    }
}
