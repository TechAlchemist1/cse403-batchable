package com.batchable.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Global exception handler for the backend. Catches common service-layer exceptions and maps them
 * to proper HTTP responses, if they are not caught in a more local context first.
 */
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

  /**
   * Handles validation errors (invalid or missing fields)
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String handleBadRequest(IllegalArgumentException e) {
    return e.getMessage();
  }

  /**
   * Handles conflicts, e.g., trying to create a resource that already exists
   */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public String handleConflict(IllegalStateException e) {
    return e.getMessage();
  }

  /**
   * Handles all other uncaught exceptions
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public String handleGeneral(Exception e) {
    return "Internal server error: " + e.getMessage();
  }
}
