package com.batchable.backend.client;

import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.model.dto.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class GoogleRoutesClient {

  private final WebClient webClient;
  private final String apiKey;

  public GoogleRoutesClient(@Qualifier("googleRoutesWebClient") WebClient webClient,
      @Value("${google.routes.api-key}") String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "Google Routes API key is not configured. Set GOOGLE_ROUTES_API_KEY.");
    }
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  /**
   * Calls Google's Directions API and returns a parsed response. Automatically includes the
   * required FieldMask header.
   */
  public DirectDirectionsResponse getDirectDirections(DirectDirectionsRequest request) throws InvalidRouteException {
    try {
      DirectDirectionsResponse.GoogleResponse googleResponse = webClient.post()
          .uri(uriBuilder -> uriBuilder.path("/directions/v2:computeRoutes")
              .queryParam("key", apiKey).build())
          .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration").bodyValue(request)
          .retrieve().bodyToMono(DirectDirectionsResponse.GoogleResponse.class).block();
      return new DirectDirectionsResponse(googleResponse);
    } catch (WebClientResponseException e) {
      throw new RuntimeException(
          "Google API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
    }
  }

  /**
   * Calls Google's Directions API and returns a parsed response. Automatically includes the
   * required FieldMask header.
   */
  public RouteDirectionsResponse getRouteDirections(RouteDirectionsRequest request,
      boolean includeLegs) throws InvalidRouteException {
    String fieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";
    if (includeLegs) {
      fieldMask += ",routes.legs";
    }
    try {
      RouteDirectionsResponse.GoogleResponse googleResponse = webClient.post()
          .uri(uriBuilder -> uriBuilder.path("/directions/v2:computeRoutes")
              .queryParam("key", apiKey).build())
          .header("X-Goog-FieldMask", fieldMask).bodyValue(request).retrieve()
          .bodyToMono(RouteDirectionsResponse.GoogleResponse.class).block();
      return new RouteDirectionsResponse(googleResponse);
    } catch (WebClientResponseException e) {
      throw new RuntimeException(
          "Google API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
    }
  }

  /**
   * Calls Google's Distance Matrix API and returns travel times/distances between multiple origins
   * and destinations.
   */
  public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
    try {
      // Get raw JSON as list of elements
      List<DistanceMatrixResponse.MatrixElement> elements = webClient.post()
          .uri(uriBuilder -> uriBuilder.path("/distanceMatrix/v2:computeRouteMatrix")
              .queryParam("key", apiKey).build())
          .header("X-Goog-FieldMask",
              "originIndex,destinationIndex,duration,distanceMeters,condition")
          .bodyValue(request).retrieve().bodyToFlux(DistanceMatrixResponse.MatrixElement.class)
          .collectList().block();

      // Now convert to your 2D int matrix
      return new DistanceMatrixResponse(elements, true);

    } catch (WebClientResponseException e) {
      throw new RuntimeException(
          "Google API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to call Google Routes API", e);
    }
  }
}
