package com.batchable.backend.db.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class MenuItem {
    public final long id;
    @JsonProperty("restaurant") 
    public final long restaurantId;
    public final String name;

    public MenuItem(long id, long restaurantId, String name) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
    }
}
