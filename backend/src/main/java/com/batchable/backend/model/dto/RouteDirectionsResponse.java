package com.batchable.backend.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a simplified response from the Google Routes API, with optional legs.
 */
public class RouteDirectionsResponse {

  private String polyline;
  private int distanceMeters;
  private int durationSeconds;

  /** Optional list of legs, if requested */
  private List<Leg> legs;

  public RouteDirectionsResponse() {}

  public RouteDirectionsResponse(GoogleResponse googleResponse) {
    if (googleResponse != null && googleResponse.getRoutes() != null
        && !googleResponse.getRoutes().isEmpty()) {

      GoogleResponse.Route firstRoute = googleResponse.getRoutes().get(0);

      if (firstRoute.getPolyline() != null) {
        this.polyline = firstRoute.getPolyline().getEncodedPolyline();
      }

      this.distanceMeters = firstRoute.getDistanceMeters();

      String durationString = firstRoute.getDuration();
      if (durationString != null && durationString.endsWith("s")) {
        this.durationSeconds =
            Integer.parseInt(durationString.substring(0, durationString.length() - 1));
      }

      if (distanceMeters < 0 || durationSeconds < 0) {
        throw new IllegalStateException(
            "Parsed distance or duration from Google API was negative.");
      }

      // Parse legs if present
      if (firstRoute.getLegs() != null && !firstRoute.getLegs().isEmpty()) {
        this.legs = new ArrayList<>();
        for (GoogleResponse.Route.Leg googleLeg : firstRoute.getLegs()) {
          Leg leg = new Leg();
          leg.setDistanceMeters(googleLeg.getDistanceMeters());
          this.legs.add(leg);
        }
      }
    }
  }

  // --- getters and setters ---
  public String getPolyline() {
    return polyline;
  }

  public void setPolyline(String polyline) {
    this.polyline = polyline;
  }

  public int getDistanceMeters() {
    return distanceMeters;
  }

  public void setDistanceMeters(int distanceMeters) {
    this.distanceMeters = distanceMeters;
  }

  public int getDurationSeconds() {
    return durationSeconds;
  }

  public void setDurationSeconds(int durationSeconds) {
    this.durationSeconds = durationSeconds;
  }

  public List<Leg> getLegs() {
    return legs;
  }

  public void setLegs(List<Leg> legs) {
    this.legs = legs;
  }

  // --- Nested Leg class ---
  public static class Leg {
    private int distanceMeters;
    private int durationSeconds;
    private String startAddress;
    private String endAddress;

    public int getDistanceMeters() {
      return distanceMeters;
    }

    public void setDistanceMeters(int distanceMeters) {
      this.distanceMeters = distanceMeters;
    }

    public int getDurationSeconds() {
      return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
      this.durationSeconds = durationSeconds;
    }

    public String getStartAddress() {
      return startAddress;
    }

    public void setStartAddress(String startAddress) {
      this.startAddress = startAddress;
    }

    public String getEndAddress() {
      return endAddress;
    }

    public void setEndAddress(String endAddress) {
      this.endAddress = endAddress;
    }
  }

  // --- Nested GoogleResponse class ---
  public static class GoogleResponse {
    private List<Route> routes;

    public List<Route> getRoutes() {
      return routes;
    }

    public void setRoutes(List<Route> routes) {
      this.routes = routes;
    }

    public static class Route {
      private Polyline polyline;
      private int distanceMeters;
      private String duration;
      private List<Leg> legs;

      public Polyline getPolyline() {
        return polyline;
      }

      public void setPolyline(Polyline polyline) {
        this.polyline = polyline;
      }

      public int getDistanceMeters() {
        return distanceMeters;
      }

      public void setDistanceMeters(int distanceMeters) {
        this.distanceMeters = distanceMeters;
      }

      public String getDuration() {
        return duration;
      }

      public void setDuration(String duration) {
        this.duration = duration;
      }

      public List<Leg> getLegs() {
        return legs;
      }

      public void setLegs(List<Leg> legs) {
        this.legs = legs;
      }

      public static class Polyline {
        private String encodedPolyline;

        public String getEncodedPolyline() {
          return encodedPolyline;
        }

        public void setEncodedPolyline(String encodedPolyline) {
          this.encodedPolyline = encodedPolyline;
        }
      }

      public static class Leg {
        private int distanceMeters;

        public int getDistanceMeters() {
          return distanceMeters;
        }

        public void setDistanceMeters(int distanceMeters) {
          this.distanceMeters = distanceMeters;
        }
      }
    }
  }
}