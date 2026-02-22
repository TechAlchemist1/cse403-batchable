package com.batchable.backend.exception;

// For when a route API response cannot be parsed
public class InvalidRouteException extends Exception {

  public InvalidRouteException(String msg) {
    super(msg);
  }

  public InvalidRouteException() {
    super();
  }
}
