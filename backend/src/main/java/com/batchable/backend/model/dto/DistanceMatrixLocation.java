package com.batchable.backend.model.dto;

// Represents a waypoint for the google routematrix API.
public class DistanceMatrixLocation {
  private Waypoint waypoint;

  public DistanceMatrixLocation() {}

  public DistanceMatrixLocation(Waypoint waypoint) {
    this.waypoint = waypoint;
  }

  public Waypoint getWaypoint() {
    return waypoint;
  }

  public void setWaypoint(Waypoint waypoint) {
    this.waypoint = waypoint;
  }
}
