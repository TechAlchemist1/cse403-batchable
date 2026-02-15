package com.batchable.backend.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * OrderWebSocketPublisher handles broadcasting real-time updates to connected frontend clients over
 * WebSockets.
 *
 * Responsibilities: - Expose refreshOrderData() for pushing updates - Encapsulate the
 * SimpMessagingTemplate so other services don't talk to WebSockets directly
 *
 * Usage: - Inject this into BatchingManager or OrderService - Call refreshOrderData(payload)
 * whenever orders or batches change
 */
@Service
public class OrderWebSocketPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public OrderWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  /**
   * Notifies websocket listeners that order or batch data has been updated.
   *
   * Behavior: - Sends an empty payload ("") to all clients subscribed to "/topic/orders" - Clients
   * should interpret this as a signal to refresh data via REST API
   *
   * Design decision: - Payload is intentionally empty because we only need a "change signal" -
   * Keeps WebSocket simple and decouples it from the actual data structure
   */
  public void refreshOrderData() {
    messagingTemplate.convertAndSend("/topic/orders", "");
  }

}
