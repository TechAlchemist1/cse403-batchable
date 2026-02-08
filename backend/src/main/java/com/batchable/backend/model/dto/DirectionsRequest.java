package com.batchable.backend.model.dto;

/**
 * DTO (Data Transfer Object) representing a request to the Google Directions API.
 * 
 * Responsibilities: - Holds the data needed to compute directions between an origin and a
 * destination. - Used by GoogleRoutesClient to serialize into JSON for the API request.
 */
public class DirectionsRequest {

  // Starting location for the route.
  // Can be an address, city, or coordinates (e.g., "Seattle, WA")
  private String origin;

  // Ending location for the route.
  // Can be an address, city, or coordinates (e.g., "Redmond, WA")
  private String destination;

  // Travel mode for the route. Supported values include:
  // "DRIVE", "WALK", "BICYCLE", "TRANSIT"
  private String travelMode;

  /**
   * Default constructor required for Spring / Jackson to deserialize JSON.
   */
  public DirectionsRequest() {}

  /**
   * Convenience constructor to quickly create a DirectionsRequest.
   *
   * @param origin Starting location
   * @param destination Ending location
   * @param travelMode Mode of travel
   */
  public DirectionsRequest(String origin, String destination, String travelMode) {
    this.origin = origin;
    this.destination = destination;
    this.travelMode = travelMode;
  }

  /** Getter for origin */
  public String getOrigin() {
    return origin;
  }

  /** Setter for origin */
  public void setOrigin(String origin) {
    this.origin = origin;
  }

  /** Getter for destination */
  public String getDestination() {
    return destination;
  }

  /** Setter for destination */
  public void setDestination(String destination) {
    this.destination = destination;
  }

  /** Getter for travelMode */
  public String getTravelMode() {
    return travelMode;
  }

  /** Setter for travelMode */
  public void setTravelMode(String travelMode) {
    this.travelMode = travelMode;
  }
}
