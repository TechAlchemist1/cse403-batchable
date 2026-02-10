package models;

public final class Restaurant {
    public final long id;
    public final String name;
    public final String location;

    public Restaurant(long id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }
}
