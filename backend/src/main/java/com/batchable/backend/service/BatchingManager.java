package com.batchable.backend.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

@Service
public class BatchingManager {
  private final OrderWebSocketPublisher publisher;

  public BatchingManager(OrderWebSocketPublisher publisher) {
    this.publisher = publisher;
  }

  // Thread-safe list
  private final List<Consumer<long[]>> batchChangeListeners = new CopyOnWriteArrayList<>();

  /**
   * Registers a listener that will be called whenever batches change.
   *
   * @param handler callback receiving driver IDs whose batches changed
   * @throws IllegalArgumentException if handler is null
   */
  public void onBatchesChange(Consumer<long[]> handler) {
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }

    batchChangeListeners.add(handler);
  }

  /**
   * Call this internally whenever batches are modified.
   */
  private void emitBatchChange(long[] updatedBatchIds) {
    for (Consumer<long[]> listener : batchChangeListeners) {
      listener.accept(updatedBatchIds);
    }
    // Push update to frontend via WebSocket
    publisher.refreshOrderData();
  }
}
