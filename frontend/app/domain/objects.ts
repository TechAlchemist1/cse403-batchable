// The type parameter indicates the type it identifies, to avoid mix-ups
export interface Id<T extends string> {
  type: T;
  id: number;
}

export interface DomainObject<T extends string = string> {
  id: Id<T>;
}

export function fakeId<I extends string>(type: I) {
  return {type, id: -3141592653};
}

export type IdKey<T extends DomainObject> = {
  [K in keyof T]: T[K] extends Id<string> ? K : never;
}[keyof T];

export interface WorldLocation {
  address: string;
}

export interface Polyline {
  encoded: string;
}

export interface PhoneNumber {
  compact: string;
}

// domain object schemas
export interface Restaurant extends DomainObject<'Restaurant'> {
  location: WorldLocation;
  name: string;
}

export interface Driver extends DomainObject<'Driver'> {
  phoneNumber: PhoneNumber;
  restaurant: Restaurant['id'];
  name: string;
  onShift: boolean;
}

export const ORDER_STATES = [
  'cooking',
  'cooked',
  'driving',
  'delivered',
] as const;

export interface Order extends DomainObject<'Order'> {
  restaurant: Restaurant['id'];
  destination: WorldLocation;
  itemNames: string[];
  initialTime: Date;
  deliveryTime: Date; // estimated promised time (changes when state >= delivered)
  cookedTime: Date; // estimated prep time (changes when state >= cooked)
  state: (typeof ORDER_STATES)[number];
  highPriority: boolean;
  currentBatch: Batch['id'] | null;
}

type OrderStateBefore<
  T extends Order['state'],
  S = typeof ORDER_STATES,
> = S extends readonly [...infer Head, infer Tail]
  ? T extends Tail
    ? Head[number]
    : OrderStateBefore<T, Head>
  : never;

export function isStateBefore(
  a: Order['state'],
  b: Order['state'],
): a is OrderStateBefore<typeof b> {
  return ORDER_STATES.indexOf(a) < ORDER_STATES.indexOf(b);
}

export function nextStateAfter(
  a: OrderStateBefore<'delivered'>,
): Order['state'] {
  return ORDER_STATES[ORDER_STATES.indexOf(a) + 1];
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
