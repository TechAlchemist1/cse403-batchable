package com.batchable.backend.model.dto;

import java.util.List;

/**
 * DTO (Data Transfer Object) representing a request to the Google Distance Matrix API.
 *
 * Responsibilities: - Holds the origins and destinations for which travel distances and durations
 * should be computed. - Uses Waypoint objects for type safety and clarity. - Serialized by
 * GoogleRoutesClient into JSON for API requests.
 */
public class DistanceMatrixRequest {

  // Represents starting points (addresses, cities, or coordinates), where 
  // each address is wrapped in a Waypoint which is wrapped in a DistanceMatrixLocation
  // as per the google routematrix API.
  // Example: [new DistanceMatrixLocation(new Waypoint("Seattle, WA")),
  // new DistanceMatrixLocation(new Waypoint("Redmond, WA"))]
  private List<DistanceMatrixLocation> origins;

  // Represents ending points (addresses, cities, or coordinates).
  private List<DistanceMatrixLocation> destinations;

  // Travel mode: "DRIVE", "WALK", "BICYCLE", "TRANSIT"
  // Determines how travel distances and times are calculated
  private String travelMode;

  /** Default constructor required by Spring / Jackson for JSON deserialization. */
  public DistanceMatrixRequest() {}

  /**
   * Convenience constructor to quickly create a DistanceMatrixRequest.
   *
   * @param origins List of starting Waypoints
   * @param destinations List of ending Waypoints
   * @param travelMode Mode of travel
   */
  public DistanceMatrixRequest(List<DistanceMatrixLocation> origins,
      List<DistanceMatrixLocation> destinations, String travelMode) {
    this.origins = origins;
    this.destinations = destinations;
    this.travelMode = travelMode;
  }

  /** Getter for origins */
  public List<DistanceMatrixLocation> getOrigins() {
    return origins;
  }

  /** Setter for origins */
  public void setOrigins(List<DistanceMatrixLocation> origins) {
    this.origins = origins;
  }

  /** Getter for destinations */
  public List<DistanceMatrixLocation> getDestinations() {
    return destinations;
  }

  /** Setter for destinations */
  public void setDestinations(List<DistanceMatrixLocation> destinations) {
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
