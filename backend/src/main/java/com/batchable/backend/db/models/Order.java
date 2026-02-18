package com.batchable.backend.db.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    public final String itemNamesJson;
    public final Instant initialTime;
    public final Instant deliveryTime;
    public final Instant cookedTime;
    public final State state;
    public final boolean highPriority;
    public final Long batchId;

    @JsonCreator
    public Order(
        @JsonProperty("id") long id,
        @JsonProperty("restaurant") long restaurantId,
        @JsonProperty("destination") String destination,
        @JsonProperty("itemNames") String itemNamesJson,
        @JsonProperty("initialTime") Instant initialTime,
        @JsonProperty("deliveryTime") Instant deliveryTime,
        @JsonProperty("cookedTime") Instant cookedTime,
        @JsonProperty("state") State state,
        @JsonProperty("highPriority") boolean highPriority,
        @JsonProperty("currentBatch") Long batchId
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
