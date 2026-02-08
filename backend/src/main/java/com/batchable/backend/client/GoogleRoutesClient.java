package com.batchable.backend.client;

// DTOs that represent request + response payloads
import com.batchable.backend.model.dto.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

// Marks this class as a Spring-managed service component
// It will be automatically detected and instantiated at startup
import org.springframework.stereotype.Service;

// Spring’s HTTP client for calling external APIs
import org.springframework.web.reactive.function.client.WebClient;

// Exception thrown when the HTTP response is a 4xx or 5xx
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class GoogleRoutesClient {

  // A preconfigured WebClient for Google Routes
  // (base URL + headers already set in WebClientConfig)
  private final WebClient webClient;

  // API key loaded from application.yml
  private final String apiKey;

  /**
   * Constructor injection:
   *
   * - Spring finds the WebClient bean named "googleRoutesWebClient" - Spring reads
   * google.routes.api-key from application.yml - Spring passes both into this constructor
   */
  public GoogleRoutesClient(@Qualifier("googleRoutesWebClient") WebClient webClient,
      @Value("${google.routes.api-key}") String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  /**
   * Calls Google's Directions API and returns a parsed response.
   *
   * @param request DTO containing origin, destination, and options
   * @return DirectionsResponse mapped from Google's JSON response
   */
  public DirectionsResponse getDirections(DirectionsRequest request) {
    try {
      return webClient
          // Start building an HTTP POST request
          .post()

          // Build the request URI:
          // base-url (from config) + path + query params
          .uri(uriBuilder -> uriBuilder.path("/directions/v2:computeRoutes")
              .queryParam("key", apiKey).build())

          // Serialize the request DTO into JSON and use it as the body
          .bodyValue(request)

          // Execute the request and prepare to handle the response
          .retrieve()

          // Convert the JSON response into a DirectionsResponse object
          .bodyToMono(DirectionsResponse.class)

          // Block the current thread until the response arrives
          // (acceptable here since this is not a reactive controller)
          .block();

    } catch (WebClientResponseException e) {
      // The request reached Google, but Google returned a 4xx or 5xx
      throw new RuntimeException(
          "Google API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
    } catch (Exception e) {
      // Network issues, serialization errors, or unexpected failures
      throw new RuntimeException("Failed to call Google Routes API", e);
    }
  }

  /**
   * Calls Google's Distance Matrix API and returns travel times/distances between multiple origins
   * and destinations.
   *
   * @param request DTO containing origins and destinations
   * @return DistanceMatrixResponse parsed from Google's response
   */
  public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
    try {
      return webClient.post()
          .uri(uriBuilder -> uriBuilder.path("/distanceMatrix/v2:computeRouteMatrix")
              .queryParam("key", apiKey).build())
          .bodyValue(request).retrieve().bodyToMono(DistanceMatrixResponse.class).block();

    } catch (WebClientResponseException e) {
      // Google responded with an error status code
      throw new RuntimeException(
          "Google API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
    } catch (Exception e) {
      // Any other unexpected failure
      throw new RuntimeException("Failed to call Google Routes API", e);
    }
  }
}
