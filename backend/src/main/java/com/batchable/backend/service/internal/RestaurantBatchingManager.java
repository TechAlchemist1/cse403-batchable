package com.batchable.backend.service.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

/**
 * Manages order batching for a single restaurant.
 *
 * Responsibilities:
 * - Holds tentative, ready, and active batches for this restaurant.
 * - Assigns batches to drivers when ready and computes routes/duration.
 * - Emits events when batches change or become active.
 * - Periodically checks for expired tentative batches.
 */
public class RestaurantBatchingManager {

  private final long restaurantId;
  private final String restaurantAddress;
  private final OrderWebSocketPublisher publisher;
  private final BatchingAlgorithm batchingAlgorithm;
  private final List<Consumer<Batches>> batchesChangeListeners = new ArrayList<>();
  private final List<Consumer<Batch>> batchBecomeActiveListeners = new ArrayList<>();
  private final Batches batches = new Batches();
  private final Queue<Driver> readyDrivers = new LinkedList<Driver>();
  private final RouteService routeService;

  /**
   * Constructs a batching manager for a single restaurant.
   *
   * @param restaurantId the restaurant ID
   * @param restaurantAddress the restaurant's address (used for routing)
   * @param publisher websocket publisher to notify clients
   * @param batchingAlgorithm algorithm for forming batches
   * @param routeService service for computing route polylines and duration
   */
  public RestaurantBatchingManager(long restaurantId, String restaurantAddress,
      OrderWebSocketPublisher publisher, BatchingAlgorithm batchingAlgorithm,
      RouteService routeService) {
    this.restaurantId = restaurantId;
    this.restaurantAddress = restaurantAddress;
    this.publisher = publisher;
    this.batchingAlgorithm = batchingAlgorithm;
    this.routeService = routeService;
  }

  /**
   * Represents a batch that is ready to be assigned to a driver.
   */
  private static class ReadyBatch {
    private final List<Order> batch;

    private ReadyBatch(TentativeBatch tentativeBatch) {
      this.batch = new ArrayList<>(tentativeBatch.getBatch());
    }
  }

  /**
   * Holds all batches for this restaurant, separated by status.
   */
  public static class Batches {
    public final List<TentativeBatch> tentativeBatches = new ArrayList<>();
    public final Queue<ReadyBatch> readyBatches = new LinkedList<>();
    public final List<Batch> activeBatches = new ArrayList<>();
  }

  /**
   * Registers a listener that will be called whenever batches change.
   *
   * @param handler callback receiving the current batches
   * @throws IllegalArgumentException if handler is null
   */
  public void onBatchesChange(Consumer<Batches> handler) {
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }
    batchesChangeListeners.add(handler);
  }

  /**
   * Registers a listener that will be called whenever a batch becomes active.
   *
   * @param handler callback receiving the newly active batch
   * @throws IllegalArgumentException if handler is null
   */
  public void onBatchBecomeActive(Consumer<Batch> handler) {
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }
    batchBecomeActiveListeners.add(handler);
  }

  /** Emits batch change events to all registered listeners. */
  private void emitBatchesChange() {
    for (Consumer<Batches> listener : batchesChangeListeners) {
      listener.accept(this.batches);
    }
    publisher.refreshOrderData();
  }

  /** Emits a batch become active event and also refreshes batch state. */
  private void emitBatchBecomeActive(Batch batch) {
    for (Consumer<Batch> listener : batchBecomeActiveListeners) {
      listener.accept(batch);
    }
    emitBatchesChange();
  }

  /**
   * Adds an order to the restaurant's tentative batches.
   *
   * @param order the order to add
   */
  public void addOrder(Order order) {
    batchingAlgorithm.addOrder(batches.tentativeBatches, order, restaurantAddress);
  }

  /**
   * Checks for expired tentative batches and moves them to ready batches.
   * Then assigns ready batches to available drivers, computing route polylines
   * and expected completion times.
   */
  public void checkExpiredBatches() {
    Instant now = Instant.now();
    List<TentativeBatch> tentativeBatches = batches.tentativeBatches;
    Queue<ReadyBatch> readyBatches = batches.readyBatches;

    // Move expired tentative batches to ready batches
    for (int i = tentativeBatches.size() - 1; i >= 0; i--) {
      TentativeBatch tentativeBatch = tentativeBatches.get(i);
      if (now.isBefore(tentativeBatch.getLastAllowedCookedTime())) {
        break; // batches sorted by expiration, can stop early
      }
      tentativeBatches.remove(i);
      readyBatches.add(new ReadyBatch(tentativeBatch));
    }

    // Assign ready batches to available drivers
    int numReadyBatches = readyBatches.size();
    int numReadyDrivers = readyDrivers.size();

    for (int i = 0; i < Math.min(numReadyBatches, numReadyDrivers); i++) {
      ReadyBatch readyBatch = readyBatches.poll();
      Driver readyDriver = readyDrivers.poll();

      List<String> stops = new ArrayList<>();
      for (Order order : readyBatch.batch) {
        stops.add(order.destination);
      }

      // Compute route and duration using RouteService
      RouteDirectionsResponse resp = routeService.getRouteDirections(restaurantAddress, stops, false);
      String polyline = resp.getPolyline();
      int routeSeconds = resp.getDurationSeconds();

      // TEMPORARY: generate batch ID using UUID
      // TODO: figure out a way to get newBatchId using db
      long newBatchId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;

      Instant dispatchTime = Instant.now();
      Instant expectedCompletionTime = dispatchTime.plus(Duration.ofSeconds(routeSeconds));

      Batch newBatch = new Batch(newBatchId, readyDriver.id, polyline, dispatchTime, expectedCompletionTime);

      for (Order order : readyBatch.batch) {
        order.batchId = newBatchId;
      }

      // Add batch to active batches and emit event
      batches.activeBatches.add(newBatch);
      emitBatchBecomeActive(newBatch);
    }
  }
}
