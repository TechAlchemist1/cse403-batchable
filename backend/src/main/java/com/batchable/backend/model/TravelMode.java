package com.batchable.backend.model;

public enum TravelMode {
    DRIVE("DRIVE"),
    WALK("WALK"),
    BICYCLE("BICYCLE"),
    TRANSIT("TRANSIT");

    private final String value;

    TravelMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
