package com.batchable.backend.db.models;

public final class Driver {
    public final long id;
    public final long restaurantId;
    public final String name;
    public final String phoneNumber;
    public final boolean onShift;

    public Driver(long id, long restaurantId, String name, String phoneNumber, boolean onShift) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.onShift = onShift;
    }
}
