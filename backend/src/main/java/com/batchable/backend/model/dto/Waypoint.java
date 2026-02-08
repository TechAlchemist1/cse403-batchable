package com.batchable.backend.model.dto;

// Represents an address for the google API.
public class Waypoint {
  private String address; // Google API can also accept latLng, but address is simplest

  public Waypoint() {}

  public Waypoint(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }
}
