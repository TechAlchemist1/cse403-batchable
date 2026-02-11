package com.batchable.backend.model.dto;

import java.util.List;

/**
 * DTO (Data Transfer Object) representing a response from the Google Directions API.
 *
 * Responsibilities: - Holds the computed distance and duration between an origin and destination. -
 * Used by the service/controller layer to return JSON to clients. - Can be serialized/deserialized
 * automatically by Spring (Jackson).
 */
public class DirectionsResponse {

  // Distance in meters (e.g., 10000 meters)
  // Useful for calculations, sorting, or aggregations
  private int distanceMeters;

  // Travel duration in seconds (e.g., 900)
  // Useful for calculations, like total travel time or ETA
  private int durationSeconds;

  /**
   * Default constructor required by Spring / Jackson for JSON deserialization.
   */
  public DirectionsResponse() {}

  /**
   * Constructor to build a flat DirectionsResponse from Google’s nested response.
   *
   * @param googleResponse GoogleResponse parsed from the API JSON
   */
  public DirectionsResponse(GoogleResponse googleResponse) {
    if (googleResponse.getRoutes() != null && !googleResponse.getRoutes().isEmpty()) {
      GoogleResponse.Route firstRoute = googleResponse.getRoutes().get(0);
      this.distanceMeters = firstRoute.getDistanceMeters();
      String durationString = firstRoute.getDuration(); // always of the form "<seconds>s"
      this.durationSeconds =
          Integer.parseInt(durationString.substring(0, durationString.length() - 1));
      if (this.distanceMeters < 0 || this.durationSeconds < 0) {
        throw new IllegalStateException("Parsed distance or duration from google API was negative.");
      }
    }
  }

  /** Getter for distance in meters */
  public int getDistanceMeters() {
    return distanceMeters;
  }

  /** Setter for distance in meters */
  public void setDistanceMeters(int distanceMeters) {
    if (distanceMeters < 0) {
      throw new IllegalArgumentException("Distance must be nonnegative.");
    }
    this.distanceMeters = distanceMeters;
  }

  /** Getter for duration in seconds */
  public int getDurationSeconds() {
    return durationSeconds;
  }

  /** Setter for duration in seconds */
  public void setDurationSeconds(int durationSeconds) {
    if (durationSeconds < 0) {
      throw new IllegalArgumentException("Duration must be nonnegative.");
    }
    this.durationSeconds = durationSeconds;
  }

  /** Nested DTO matching Google’s response structure */
  public static class GoogleResponse {
    private List<Route> routes;

    public List<Route> getRoutes() {
      return routes;
    }

    public void setRoutes(List<Route> routes) {
      this.routes = routes;
    }

    public static class Route {
      private int distanceMeters;
      private String duration;

      public int getDistanceMeters() {
        return distanceMeters;
      }

      public void setDistanceMeters(int distanceMeters) {
        if (distanceMeters < 0) {
          throw new IllegalArgumentException("Distance must be nonnegative.");
        }
        this.distanceMeters = distanceMeters;
      }

      public String getDuration() {
        return duration;
      }

      public void setDuration(String duration) {
        this.duration = duration;
      }
    }
  }
}
