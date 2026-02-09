import type {
  Id,
  WorldLocation,
  Polyline,
  Order,
  MenuItem,
  Restaurant,
  Driver,
  Batch,
} from './objects';

type JSONDomainField<T> = T extends Date | WorldLocation | Polyline
  ? string
  : T extends Id<infer I>
    ? Id<I>['id']
    : T;

/**
 * For a given domain object type, this represents the post-parsing JSON representation.
 */
export type JSONDomainObject<T> = {
  [K in keyof T]: JSONDomainField<T[K]>;
};

type JSONDomainFieldParser<T> = (encoded: JSONDomainField<T>) => T;

/**
 * Converts a Polyline from post-parsing JSON format to TypeScript format.
 * @param encoded The Google-API-encoded representation of the polyline
 * @returns The TypeScript representation of the Polyline
 */
const parsePolyline: JSONDomainFieldParser<Polyline> = encoded => ({
  encoded,
});

/**
 * Converts a WorldLocation from post-parsing JSON format to TypeScript format.
 * @param address The address string of the WorldLocation
 * @returns The TypeScript representation of the WorldLocation
 */
const parseWorldLocation: JSONDomainFieldParser<WorldLocation> = address => ({
  address,
});

/**
 * Converts a Date from post-parsing JSON format to TypeScript format.
 * @param utc The UTC date string of the Date
 * @returns The TypeScript representation of the Date
 */
const parseDate: JSONDomainFieldParser<Date> = utc => new Date(utc);

/**
 * Converts a domain object Id from post-parsing JSON format to TypeScript format.
 * @param address The raw key value for the Id
 * @returns The TypeScript representation of the Id
 */
const parseId =
  <T extends string>(type: T) =>
  (id: JSONDomainField<Id<T>>): Id<T> => ({type, id});

/**
 * Parses a value by allowing null to pass through unchanged. If not null, delegates to a given function.
 * @param parse The function to parse non-null values with
 * @returns The final parser function
 */
const parseNullable =
  <T, R>(parse: (jsonValue: T) => R) =>
  (jsonValue: T | null): R | null => {
    return jsonValue === null ? null : parse(jsonValue);
  };

/**
 * An identity parsing function for use with {@link parseDomainObject}.
 * @param x The JSON value which is in need of no further parsing
 * @returns Its argument
 */
const identity = <T>(x: T) => x;

type JSONParserSpec<T> = {
  [K in keyof T]: (jsonValue: JSONDomainField<T[K]>) => T[K];
};

/**
 * Creates a parser for a given domain object type based on parsing functions for each field.
 * @param spec A mapping from domain object keys to functions which can parse their values
 * @returns A function from a post-parsing JSON representation of the given object to the full TypeScript representation of the domain object
 */
function createDomainObjectParser<T>(spec: JSONParserSpec<T>) {
  return (json: JSONDomainObject<T>): T => {
    const result = {} as T; // safe since "result" is never read until return
    for (const key in json) {
      if (!Object.hasOwn(json, key)) continue;
      result[key] = spec[key](json[key]);
    }
    return result;
  };
}

export const parseRestaurant = createDomainObjectParser<Restaurant>({
  id: parseId('Restaurant'),
  location: parseWorldLocation,
  name: identity,
});

export const parseDriver = createDomainObjectParser<Driver>({
  id: parseId('Driver'),
  phoneNumber: identity,
  restaurant: parseId('Restaurant'),
  name: identity,
  onShift: identity,
});

export const parseOrder = createDomainObjectParser<Order>({
  id: parseId('Order'),
  restaurant: parseId('Restaurant'),
  destination: parseWorldLocation,
  itemNames: identity,
  initialTime: parseDate,
  deliveryTime: parseDate,
  cookedTime: parseDate,
  state: identity,
  highPriority: identity,
  currentBatch: parseNullable(parseId('Batch')),
});

export const parseBatch = createDomainObjectParser<Batch>({
  id: parseId('Batch'),
  driver: parseId('Driver'),
  route: parsePolyline,
  dispatchTime: parseDate,
  expectedCompletionTime: parseDate,
});

export const parseMenuItem = createDomainObjectParser<MenuItem>({
  id: parseId('MenuItem'),
  restaurant: parseId('Restaurant'),
  name: identity,
});
