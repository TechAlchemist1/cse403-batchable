package com.batchable.backend.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a processed distance matrix from Google.
 *
 * Internally stores the raw Google response but provides a simple 2D int array for either
 * distanceMeters or durationSeconds.
 */
public class DistanceMatrixResponse {

  private List<MatrixElement> elements = new ArrayList<>();

  // Simplified 2D matrix view
  private int[][] matrix;


  public DistanceMatrixResponse() {}

  /**
   * Constructs a DistanceMatrixResponse from Google response elements and converts to a 2D matrix
   * of weights.
   *
   * @param elements The raw Google matrix elements
   * @param useDistance If true, matrix entries = duration in seconds; otherwise distance in meters
   */
  public DistanceMatrixResponse(List<MatrixElement> elements, boolean useTime) {
    this.elements = new ArrayList<MatrixElement>(elements);

    // Determine the size of the matrix
    int minOrigin = elements.stream().mapToInt(MatrixElement::getOriginIndex).min().orElse(-1);
    int minDest = elements.stream().mapToInt(MatrixElement::getDestinationIndex).min().orElse(-1);
    if (minOrigin < 0 || minDest < 0) {
      throw new IllegalArgumentException(
          "An origin or destination has negative index, or there are none.");
    }
    int maxOrigin = elements.stream().mapToInt(MatrixElement::getOriginIndex).max().orElse(-1);
    int maxDest = elements.stream().mapToInt(MatrixElement::getDestinationIndex).max().orElse(-1);

    matrix = new int[maxOrigin + 1][maxDest + 1];

    // Fill matrix
    for (MatrixElement e : elements) {
      int value;
      if (!useTime) {
        value = e.getDistanceMeters();
      } else {
        // Convert duration string (e.g. "1290s") to int seconds
        String durationString = e.getDuration().substring(0, e.getDuration().length() - 1);
        value = Integer.parseInt(durationString);
      }
      if (value < 0) {
        throw new IllegalArgumentException(
            "Durations and distances between origins and destinations must be nonnegative");
      }
      matrix[e.getOriginIndex()][e.getDestinationIndex()] = value;
    }
  }

  public int[][] getMatrix() {
    return matrix.clone();
  }

  public List<MatrixElement> getElements() {
    return new ArrayList<MatrixElement>(elements);
  }

  public void setElements(List<MatrixElement> elements) {
    this.elements = new ArrayList<MatrixElement>(elements);
  }

  public void setMatrix(int[][] matrix) {
    this.matrix = matrix.clone();
  }

  /** Matches one element of the Google Route Matrix response */
  public static class MatrixElement {
    private int originIndex;
    private int destinationIndex;
    private int distanceMeters;
    private String duration;
    private String condition;

    public MatrixElement() {}

    public int getOriginIndex() {
      return originIndex;
    }

    public void setOriginIndex(int originIndex) {
      if (originIndex < 0) {
        throw new IllegalArgumentException("Indices must be nonnegative.");
      }
      this.originIndex = originIndex;
    }

    public int getDestinationIndex() {
      return destinationIndex;
    }

    public void setDestinationIndex(int destinationIndex) {
      if (destinationIndex < 0) {
        throw new IllegalArgumentException("Indices must be nonnegative.");
      }
      this.destinationIndex = destinationIndex;
    }

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

    public String getCondition() {
      return condition;
    }

    public void setCondition(String condition) {
      this.condition = condition;
    }
  }
}
