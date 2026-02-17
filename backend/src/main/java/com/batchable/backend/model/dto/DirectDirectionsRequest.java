package com.batchable.backend.model.dto;

import com.batchable.backend.model.TravelMode;

/**
 * DTO (Data Transfer Object) representing a request to the Google Directions API.
 * 
 * Responsibilities:
 * - Holds the data needed to compute directions between an origin and a destination.
 * - Used by GoogleRoutesClient to serialize into JSON for the API request.
 */
public class DirectDirectionsRequest {

  // Starting location for the route.
  // Represented as a Waypoint object (address or coordinates)
  private Waypoint origin;

  // Ending location for the route.
  // Represented as a Waypoint object (address or coordinates)
  private Waypoint destination;

  // Travel mode for the route.
  private TravelMode travelMode;

  /**
   * Default constructor required for Spring / Jackson to deserialize JSON.
   */
  public DirectDirectionsRequest() {}

  /**
   * Convenience constructor to quickly create a DirectionsRequest.
   *
   * @param origin Starting location as a Waypoint
   * @param destination Ending location as a Waypoint
   * @param travelMode Mode of travel
   */
  public DirectDirectionsRequest(Waypoint origin, Waypoint destination, TravelMode travelMode) {
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
  public TravelMode getTravelMode() {
    return travelMode;
  }

  /** Setter for travelMode */
  public void setTravelMode(TravelMode travelMode) {
    this.travelMode = travelMode;
  }
}
