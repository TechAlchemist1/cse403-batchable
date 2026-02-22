package com.batchable.backend.db.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Driver {
    public final long id;
    @JsonProperty("restaurant") 
    public final long restaurantId;
    public final String name;
    public final String phoneNumber;
    public final boolean onShift;

    @JsonCreator
    public Driver(long id, long restaurantId, String name, String phoneNumber, boolean onShift) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.onShift = onShift;
    }
}
