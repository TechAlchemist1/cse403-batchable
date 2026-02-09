// The type parameter indicates the type it identifies, to avoid mix-ups
export interface Id<T extends string> {
  type: T;
  id: number;
}

export interface DomainObject<T extends string> {
  id: Id<T>;
}

export interface WorldLocation {
  address: string;
}

export interface Polyline {
  encoded: string;
}

// domain object schemas
export interface Restaurant extends DomainObject<'Restaurant'> {
  location: WorldLocation;
  name: string;
}

export interface Driver extends DomainObject<'Driver'> {
  phoneNumber: string;
  restaurant: Restaurant['id'];
  name: string;
  onShift: boolean;
}

export interface Order extends DomainObject<'Order'> {
  restaurant: Restaurant['id'];
  destination: WorldLocation;
  itemNames: string[];
  initialTime: Date;
  deliveryTime: Date; // estimated promised time (changes when state >= delivered)
  cookedTime: Date; // estimated prep time (changes when state >= cooked)
  state: 'cooking' | 'cooked' | 'driving' | 'delivered';
  highPriority: boolean;
  currentBatch: Batch['id'] | null;
}

export interface Batch extends DomainObject<'Batch'> {
  driver: Driver['id'];
  route: Polyline;
  dispatchTime: Date;
  expectedCompletionTime: Date;
}

export interface MenuItem extends DomainObject<'MenuItem'> {
  restaurant: Restaurant['id'];
  name: string;
}
