import {expect} from 'vitest';
import type {CrudApi} from '~/api/crud';
import {
  fakeId,
  type Batch,
  type DomainObject,
  type Driver,
  type MenuItem,
  type Order,
  type Restaurant,
} from '~/domain/objects';
import * as json from '~/domain/json';
import {db} from './api/common';
import {MS_PER_MINUTE} from '~/util/time';

export async function checkedCreate<T extends DomainObject>(
  api: CrudApi<T>,
  domainObject: T,
) {
  const id = await api.create(domainObject);
  if (id === null) {
    expect.fail('created object must have non-null id');
  }
  expect(id.id).toBeTypeOf('number');
  expect(id.id).not.toBe(domainObject.id.id);
  expect(await api.exists(id)).toBe(true);
  return id;
}

export async function checkedDelete<T extends DomainObject>(
  api: CrudApi<T>,
  id: T['id'],
) {
  expect(await api.delete(id)).toBe(true);
}

export function getFakeRestaurant(): Restaurant {
  return {
    id: fakeId('Restaurant'),
    location: {address: '123 Batch St, Seattle WA'},
    name: 'Batchable Kitchen',
  };
}

export function getFakeRestaurant2(): Restaurant {
  return {
    id: fakeId('Restaurant'),
    location: {address: '123 Batch St, Seattle OR'},
    name: 'Batchable Evil Kitchen',
  };
}

export function getFakeMenuItem(restaurant: Restaurant['id']): MenuItem {
  return {
    id: fakeId('MenuItem'),
    restaurant,
    name: 'Fake Cheeseburger',
  };
}

export function getFakeMenuItem2(restaurant: Restaurant['id']): MenuItem {
  return {
    id: fakeId('MenuItem'),
    restaurant,
    name: 'Shrimp Fried Rice',
  };
}

export function getFakeDriver(restaurant: Restaurant['id']): Driver {
  return {
    id: fakeId('Driver'),
    name: 'Delano',
    onShift: true,
    phoneNumber: {compact: '9817235273'},
    restaurant,
  };
}

export function getFakeDriver2(restaurant: Restaurant['id']): Driver {
  return {
    id: fakeId('Driver'),
    name: 'Qalid',
    onShift: false,
    phoneNumber: {compact: '9817237651'},
    restaurant,
  };
}

export async function checkedCreateBatch(
  driver: Driver['id'],
  batchConfig: Partial<Batch> = {},
) {
  // note: this directly interfaces with the mock database, since there is no batching algorithm
  const now = Date.now();
  const rawBatchId = db.batches.insert(
    json.batch.unparse({
      id: fakeId('Batch'),
      driver,
      dispatchTime: new Date(now - 5 * MS_PER_MINUTE),
      expectedCompletionTime: new Date(now + 15 * MS_PER_MINUTE),
      finished: false,
      route: {
        encoded: '19872AKJSDH1b3',
      },
      ...batchConfig,
    }),
  );
  if (rawBatchId === null) {
    expect.fail('created batch id must not be null');
  }
  return json.batch.field('id').parse(rawBatchId);
}

export function getFakeOrder(restaurant: Restaurant['id']): Order {
  const now = Date.now();
  return {
    id: fakeId('Order'),
    initialTime: new Date(now),
    cookedTime: new Date(now + 1e3),
    deliveryTime: new Date(now + 1e6),
    currentBatch: null,
    destination: {address: '1600 Pennsylvania Ave, WA DC'},
    highPriority: false,
    itemNames: ['Cheese Burger', 'Anti Burger'],
    restaurant,
    state: 'driving',
  };
}
