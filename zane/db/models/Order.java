package models;

import java.time.Instant;

public final class Order {
    public enum State {
        COOKING,
        COOKED,
        DRIVING,
        DELIVERED
    }

    public final long id;
    public final long restaurantId;
    public final String destination;
    public final String itemNamesJson; // JSON array stored as string
    public final Instant initialTime;
    public final Instant deliveryTime;
    public final Instant cookedTime;
    public final State state;
    public final boolean highPriority;
    public final Long batchId; // nullable

    public Order(
            long id,
            long restaurantId,
            String destination,
            String itemNamesJson,
            Instant initialTime,
            Instant deliveryTime,
            Instant cookedTime,
            State state,
            boolean highPriority,
            Long batchId
    ) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.destination = destination;
        this.itemNamesJson = itemNamesJson;
        this.initialTime = initialTime;
        this.deliveryTime = deliveryTime;
        this.cookedTime = cookedTime;
        this.state = state;
        this.highPriority = highPriority;
        this.batchId = batchId;
    }
}
