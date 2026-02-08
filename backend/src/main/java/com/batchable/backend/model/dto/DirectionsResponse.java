package com.batchable.backend.model.dto;

/**
 * DTO (Data Transfer Object) representing a response from the Google Directions API.
 *
 * Responsibilities: - Holds the computed distance and duration between an origin and destination. -
 * Used by the service/controller layer to return JSON to clients. - Can be serialized/deserialized
 * automatically by Spring (Jackson).
 */
public class DirectionsResponse {

  // Human-readable distance (e.g., "10 km", "6 miles")
  private String distanceText;

  // Distance in meters (e.g., 10000 meters)
  // Useful for calculations, sorting, or aggregations
  private int distanceMeters;

  // Human-readable travel duration (e.g., "15 mins")
  private String durationText;

  // Travel duration in seconds (e.g., 900)
  // Useful for calculations, like total travel time or ETA
  private int durationSeconds;

  /**
   * Default constructor required by Spring / Jackson for JSON deserialization.
   */
  public DirectionsResponse() {}

  /** Getter for human-readable distance */
  public String getDistanceText() {
    return distanceText;
  }

  /** Setter for human-readable distance */
  public void setDistanceText(String distanceText) {
    this.distanceText = distanceText;
  }

  /** Getter for distance in meters */
  public int getDistanceMeters() {
    return distanceMeters;
  }

  /** Setter for distance in meters */
  public void setDistanceMeters(int distanceMeters) {
    this.distanceMeters = distanceMeters;
  }

  /** Getter for human-readable duration */
  public String getDurationText() {
    return durationText;
  }

  /** Setter for human-readable duration */
  public void setDurationText(String durationText) {
    this.durationText = durationText;
  }

  /** Getter for duration in seconds */
  public int getDurationSeconds() {
    return durationSeconds;
  }

  /** Setter for duration in seconds */
  public void setDurationSeconds(int durationSeconds) {
    this.durationSeconds = durationSeconds;
  }
}
