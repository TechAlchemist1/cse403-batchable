package com.batchable.backend.model.dto;

/**
 * DTO (Data Transfer Object) representing a request to the Google Directions API.
 * 
 * Responsibilities:
 * - Holds the data needed to compute directions between an origin and a destination.
 * - Used by GoogleRoutesClient to serialize into JSON for the API request.
 */
public class DirectionsRequest {

  // Starting location for the route.
  // Represented as a Waypoint object (address or coordinates)
  private Waypoint origin;

  // Ending location for the route.
  // Represented as a Waypoint object (address or coordinates)
  private Waypoint destination;

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
   * @param origin Starting location as a Waypoint
   * @param destination Ending location as a Waypoint
   * @param travelMode Mode of travel
   */
  public DirectionsRequest(Waypoint origin, Waypoint destination, String travelMode) {
    this.origin = origin;
    this.destination = destination;
    this.travelMode = travelMode;
  }

  /** Getter for origin */
  public Waypoint getOrigin() {
    return origin;
  }

  /** Setter for origin */
  public void setOrigin(Waypoint origin) {
    this.origin = origin;
  }

  /** Getter for destination */
  public Waypoint getDestination() {
    return destination;
  }

  /** Setter for destination */
  public void setDestination(Waypoint destination) {
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
