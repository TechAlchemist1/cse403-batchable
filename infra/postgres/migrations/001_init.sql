/*
Data Layout
Our service stores and processes a variety of domain objects,
specifically the following, each of which has all of its fields and types specified.
These will be used as the basis of the database schema, as well as the runtime
representation throughout the system. Each model provided is implied to additionally
have a unique id, and when a model contains a member whose type is another model,
it is implied that that field will in fact reference a foreign key when placed into 
the database. Several types are left intentionally vague (like Location, DateTime, 
etc.) to avoid excessive specificity. Additionally, our database will store Auth0’s 
authentication and authorization information, whose schema we need not know.

model Restaurant {
	location: Location
	name: String
}
model Driver {
	phoneNumber: PhoneNumber
	restaurant: Restaurant
	name: String
	onShift: Boolean
}
model Order {
	restaurant: Restaurant
	destination: Location
	itemNames: JSONArray<String>
	initialTime: DateTime
	deliveryTime: DateTime // est. promised time (changes when state >= DELIVERED)
	cookedTime: DateTime // est. estimated prep time (changes when state >= COOKED)
	state: enum {
		COOKING, COOKED,
		DRIVING, DELIVERED
	}
	highPriority: Boolean
	currentBatch: Option<Batch>
}
model Batch {
	driver: Driver
	route: Polyline
	dispatchTime: DateTime
	expectedCompletionTime: DateTime
}
model MenuItem {
	restaurant: Restaurant
	name: String
}
*/

-- order state requires new type
CREATE TYPE order_state AS ENUM (
  'COOKING',
  'COOKED',
  'DRIVING',
  'DELIVERED'
);

-- restaurant table
CREATE TABLE Restaurant (
  id SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  location VARCHAR(100) NOT NULL
);

-- driver table
CREATE TABLE Driver (
  id SERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone_number VARCHAR(100) NOT NULL,
  on_shift BOOLEAN NOT NULL,
  restaurant_id INTEGER NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant(id)
);

-- create the batches
-- poly line stored as text no size constraint up to a gb
CREATE TABLE Batch (
  id SERIAL PRIMARY KEY,
  driver_id INTEGER NOT NULL,
  route TEXT NOT NULL,
  dispatch_time TIMESTAMP NOT NULL,
  expected_completion_time TIMESTAMP NOT NULL,
  FOREIGN KEY (driver_id) REFERENCES Driver(id)
);

-- create the orders
-- destination assumed to be under 100 chars
CREATE TABLE "Order" (
  id SERIAL PRIMARY KEY,
  restaurant_id INTEGER NOT NULL,
  destination VARCHAR(100) NOT NULL,
  item_names JSON NOT NULL,
  initial_time TIMESTAMP NOT NULL,
  delivery_time TIMESTAMP,
  cooked_time TIMESTAMP,
  state order_state NOT NULL,
  high_priority BOOLEAN NOT NULL,
  batch_id INTEGER,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant(id),
  FOREIGN KEY (batch_id) REFERENCES Batch(id)
);

CREATE TABLE Menu_Item (
  id SERIAL PRIMARY KEY,
  restaurant_id INTEGER NOT NULL,
  name VARCHAR(100) NOT NULL,
  FOREIGN KEY (restaurant_id) REFERENCES Restaurant(id)
);
