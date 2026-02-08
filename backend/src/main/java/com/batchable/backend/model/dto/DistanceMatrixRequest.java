package com.batchable.backend.model.dto;

import java.util.List;

/**
 * DTO (Data Transfer Object) representing a request to the Google Distance Matrix API.
 *
 * Responsibilities: - Holds the origins and destinations for which travel distances and durations
 * should be computed. - Used by GoogleRoutesClient to serialize into JSON and send to the Google
 * API.
 */
public class DistanceMatrixRequest {

  // List of starting points (addresses, cities, or coordinates)
  // Example: ["Seattle, WA", "Redmond, WA"]
  private List<String> origins;

  // List of ending points (addresses, cities, or coordinates)
  // Example: ["Bellevue, WA", "Kirkland, WA"]
  private List<String> destinations;

  // Travel mode: "DRIVE", "WALK", "BICYCLE", "TRANSIT"
  // Determines how travel distances and times are calculated
  private String travelMode;

  /**
   * Default constructor required by Spring / Jackson for JSON deserialization.
   */
  public DistanceMatrixRequest() {}

  /**
   * Convenience constructor to quickly create a DistanceMatrixRequest.
   *
   * @param origins List of starting points
   * @param destinations List of ending points
   * @param travelMode Mode of travel
   */
  public DistanceMatrixRequest(List<String> origins, List<String> destinations, String travelMode) {
    this.origins = origins;
    this.destinations = destinations;
    this.travelMode = travelMode;
  }

  /** Getter for origins */
  public List<String> getOrigins() {
    return origins;
  }

  /** Setter for origins */
  public void setOrigins(List<String> origins) {
    this.origins = origins;
  }

  /** Getter for destinations */
  public List<String> getDestinations() {
    return destinations;
  }

  /** Setter for destinations */
  public void setDestinations(List<String> destinations) {
    this.destinations = destinations;
  }

  /** Getter for travel mode */
  public String getTravelMode() {
    return travelMode;
  }

  /** Setter for travel mode */
  public void setTravelMode(String travelMode) {
    this.travelMode = travelMode;
  }
}
