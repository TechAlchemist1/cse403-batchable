package com.batchable.backend.model.dto;

import java.util.List;

/**
 * DTO (Data Transfer Object) representing the response from the Google Distance Matrix API.
 *
 * Responsibilities: - Holds the computed distances and durations between multiple origins and
 * destinations. - Used by the service/controller layer to return JSON to clients. - Can be
 * serialized/deserialized automatically by Spring (via Jackson).
 */
public class DistanceMatrixResponse {

  /**
   * distancesMeters[i][j] = distance from origins[i] to destinations[j] in meters.
   * This allows the service/controller to know distances for all origin-destination pairs.
   */
  private List<List<Integer>> distancesMeters;

  /**
   * durationsSeconds[i][j] = travel time from origins[i] to destinations[j] in seconds.
   * 
   * Example: durationsSeconds = [ [900, 1200], // Seattle → Bellevue, Seattle → Kirkland [600, 900]
   * // Redmond → Bellevue, Redmond → Kirkland ]
   *
   * Useful for computing travel times, ETAs, or sorting by fastest route.
   */
  private List<List<Integer>> durationsSeconds;

  /**
   * Default constructor required by Spring / Jackson for JSON deserialization.
   */
  public DistanceMatrixResponse() {}

  /** Getter for distances in meters */
  public List<List<Integer>> getDistancesMeters() {
    return distancesMeters;
  }

  /** Setter for distances in meters */
  public void setDistancesMeters(List<List<Integer>> distancesMeters) {
    this.distancesMeters = distancesMeters;
  }

  /** Getter for durations in seconds */
  public List<List<Integer>> getDurationsSeconds() {
    return durationsSeconds;
  }

  /** Setter for durations in seconds */
  public void setDurationsSeconds(List<List<Integer>> durationsSeconds) {
    this.durationsSeconds = durationsSeconds;
  }
}
