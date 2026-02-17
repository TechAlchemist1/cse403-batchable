package com.batchable.backend.model.dto;

import java.util.ArrayList;
import java.util.List;
import com.batchable.backend.model.TravelMode;

/**
 * Represents a route directions request that supports multiple stops.
 *
 * Extends {@link DirectDirectionsRequest} to allow specifying intermediate waypoints between the
 * origin and destination.
 *
 * Responsibilities: - Holds origin and destination waypoints (inherited from
 * DirectDirectionsRequest) - Holds optional intermediate waypoints for multi-stop routes - Contains
 * travel mode information (e.g., DRIVING, WALKING)
 */
public class RouteDirectionsRequest extends DirectDirectionsRequest {

  /** Optional intermediate stops along the route */
  private List<Waypoint> intermediates = new ArrayList<>();

  /** Default constructor required for deserialization or empty request creation */
  public RouteDirectionsRequest() {
    super();
  }

  /**
   * Constructs a RouteDirectionsRequest with origin, destination, intermediates, and travel mode.
   *
   * @param origin the starting point of the route
   * @param destination the ending point of the route
   * @param intermediates optional intermediate waypoints along the route
   * @param travelMode the mode of travel (e.g., DRIVING, WALKING)
   */
  public RouteDirectionsRequest(Waypoint origin, Waypoint destination, List<Waypoint> intermediates,
      TravelMode travelMode) {
    super(origin, destination, travelMode);
    this.intermediates = intermediates != null ? intermediates : new ArrayList<>();
  }

  /** Returns the list of intermediate waypoints along the route */
  public List<Waypoint> getIntermediates() {
    return intermediates;
  }

  /**
   * Sets the intermediate waypoints along the route. Ensures the list is never null.
   *
   * @param intermediates the intermediate waypoints to set
   */
  public void setIntermediates(List<Waypoint> intermediates) {
    this.intermediates = intermediates != null ? intermediates : new ArrayList<>();
  }
}
