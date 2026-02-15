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

/**
 * Represents a method of converting field values between a TypeScript domain object representation, and a post-parsing JSON representation. `parse` converts from JSON to TypeScript, and `unparse` vice-versa.
 */
interface JSONFieldParserPair<T> {
  parse: (json: JSONDomainField<T>) => T;
  unparse: (domain: T) => JSONDomainField<T>;
}

const parsePolyline: JSONFieldParserPair<Polyline> = {
  parse(encoded) {
    return {encoded};
  },
  unparse(polyline) {
    return polyline.encoded;
  },
};

const parseWorldLocation: JSONFieldParserPair<WorldLocation> = {
  parse(address) {
    return {address};
  },
  unparse(loc) {
    return loc.address;
  },
};

const parseDate: JSONFieldParserPair<Date> = {
  parse(utc) {
    return new Date(utc);
  },
  unparse(date) {
    return date.toUTCString();
  },
};

const parseId = <T extends string>(type: T): JSONFieldParserPair<Id<T>> => ({
  parse(id) {
    return {type, id};
  },
  unparse(id) {
    return id.id;
  },
});

const parseNullable = <T>(
  parserPair: JSONFieldParserPair<T>,
): JSONFieldParserPair<T | null> => ({
  parse(json) {
    return json === null ? null : parserPair.parse(json);
  },
  unparse(domain) {
    return domain === null ? null : parserPair.unparse(domain);
  },
});

const identity = {
  parse<T>(x: T) {
    return x;
  },
  unparse<T>(x: T) {
    return x;
  },
};

/**
 * Represents a method of converting domain objects between a TypeScript domain object representation, and a post-parsing JSON representation. `parse` converts from JSON to TypeScript, and `unparse` vice-versa.
 */
export interface JSONParserPair<T> {
  parse(json: JSONDomainObject<T>): T;
  unparse(domain: T): JSONDomainObject<T>;
}

/**
 * Creates a parser for a given domain object type based on parsing functions for each field.
 * @param spec A mapping from domain object keys to parser pairs which can parse their JSON-like values
 * @returns A parser pair from a post-parsing JSON representation of the given object to the full TypeScript representation of the domain object
 */
function createDomainObjectParserPair<T>(spec: {
  [K in keyof T]: JSONFieldParserPair<T[K]>;
}): JSONParserPair<T> {
  return {
    parse(json: JSONDomainObject<T>): T {
      const domainObject = {} as T; // safe since "result" isn't read until return
      for (const key in spec) {
        if (!Object.hasOwn(spec, key)) continue;
        domainObject[key] = spec[key].parse(json[key]);
      }
      return domainObject;
    },
    unparse(domainObject: T): JSONDomainObject<T> {
      const json = {} as JSONDomainObject<T>; // safe since "result" isn't read until return
      for (const key in spec) {
        if (!Object.hasOwn(spec, key)) continue;
        json[key] = spec[key].unparse(domainObject[key]);
      }
      return json;
    },
  };
}

export const restaurant = createDomainObjectParserPair<Restaurant>({
  id: parseId('Restaurant'),
  location: parseWorldLocation,
  name: identity,
});

export const driver = createDomainObjectParserPair<Driver>({
  id: parseId('Driver'),
  phoneNumber: identity,
  restaurant: parseId('Restaurant'),
  name: identity,
  onShift: identity,
});

export const order = createDomainObjectParserPair<Order>({
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

export const batch = createDomainObjectParserPair<Batch>({
  id: parseId('Batch'),
  driver: parseId('Driver'),
  route: parsePolyline,
  dispatchTime: parseDate,
  expectedCompletionTime: parseDate,
});

export const menuItem = createDomainObjectParserPair<MenuItem>({
  id: parseId('MenuItem'),
  restaurant: parseId('Restaurant'),
  name: identity,
});
