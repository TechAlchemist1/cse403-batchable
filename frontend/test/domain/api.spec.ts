import {describe, it, expect} from 'vitest';
import {CrudApi} from '~/api/crud';
import {driverApi} from '~/api/endpoints/driver';
import {menuApi} from '~/api/endpoints/menu';
import {orderApi} from '~/api/endpoints/order';
import {restaurantApi} from '~/api/endpoints/restaurant';
import {
  fakeId,
  type DomainObject,
  type Driver,
  type MenuItem,
  type Order,
  type Restaurant,
} from '~/domain/objects';

async function checkedCreate<T extends DomainObject>(
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

async function expectReadbackCreated<T extends DomainObject>(
  api: CrudApi<T>,
  domainObject: T,
) {
  const id = await checkedCreate(api, domainObject);
  const readback = await api.read(id);
  if (readback === null) {
    expect.fail('read-back domain object should exist');
  }
  expect(readback.id.id).toBe(id.id);
  domainObject.id.id = readback.id.id;
  expect(readback).toEqual(domainObject);
  return id;
}

async function expectMissingDeleted<T extends DomainObject>(
  api: CrudApi<T>,
  domainObject: T,
  tryBrokenRud: boolean = true,
) {
  const id = await checkedCreate(api, domainObject);
  const readback = await api.read(id);
  if (readback === null) {
    expect.fail('read-back domain object should exist prior to deletion');
  }
  const deleted = await api.delete(id);
  expect(deleted).toBe(true);
  expect(await api.exists(id)).toBe(false);
  if (tryBrokenRud) {
    expect(await api.read(id)).toBe(null);
    expect(await api.delete(id)).toBe(false);
    expect(await api.update(readback)).toBe(false);
  }
}

async function expectUpdatedChanged<T extends DomainObject>(
  api: CrudApi<T>,
  domainObject: T,
  domainObject2: T,
) {
  const id = await checkedCreate(api, domainObject);

  const changed: T = {
    ...domainObject2,
    id,
  };
  const updated = await api.update(changed);
  expect(updated).toBe(true);

  const readback = await api.read(id);
  expect(readback).toEqual(changed);

  return id;
}

function getFakeRestaurant(): Restaurant {
  return {
    id: fakeId('Restaurant'),
    location: {address: '123 Batch St, Seattle WA'},
    name: 'Batchable Kitchen',
  };
}

function getFakeRestaurant2(): Restaurant {
  return {
    id: fakeId('Restaurant'),
    location: {address: '123 Batch St, Seattle OR'},
    name: 'Batchable Evil Kitchen',
  };
}

describe('/restaurant endpoint', () => {
  async function expectArrayRetrival<T extends DomainObject>(
    itemApi: CrudApi<T>,
    getItems: (id: Restaurant['id']) => Promise<T[] | null>,
    getFake: (id: Restaurant['id']) => T,
    getFake2: (id: Restaurant['id']) => T,
  ) {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const item = await checkedCreate(itemApi, getFake(restaurant));
    const restaurant2 = await checkedCreate(
      restaurantApi,
      getFakeRestaurant2(),
    );
    const item2 = await checkedCreate(itemApi, getFake2(restaurant2));
    const restaurantItems = [await itemApi.read(item)];
    const restaurant2Items = [await itemApi.read(item2)];
    const actualRestaurantItems = await getItems(restaurant);
    const actualRestaurant2Items = await getItems(restaurant2);

    expect(actualRestaurant2Items).not.toBe(null);
    expect(actualRestaurantItems).not.toBe(null);
    expect(actualRestaurantItems).toEqual(restaurantItems);
    expect(actualRestaurant2Items).toEqual(restaurant2Items);

    expect(
      await getItems({
        type: 'Restaurant',
        id: 1e80,
      }),
    ).toBe(null);
  }

  it('can create and read back a restaurant', async () => {
    await expectReadbackCreated(restaurantApi, getFakeRestaurant());
  });

  it('is gone after it is deleted', async () => {
    await expectMissingDeleted(restaurantApi, getFakeRestaurant());
  });

  it('is changed after it is updated', async () => {
    await expectUpdatedChanged(
      restaurantApi,
      getFakeRestaurant(),
      getFakeRestaurant2(),
    );
  });

  it('can retrieve orders', async () => {
    await expectArrayRetrival(
      orderApi,
      id => restaurantApi.getOrders(id),
      getFakeOrder,
      getFakeOrder,
    );
  });

  it('can retrieve drivers', async () => {
    await expectArrayRetrival(
      driverApi,
      id => restaurantApi.getDrivers(id),
      getFakeDriver,
      getFakeDriver2,
    );
  });

  it('can retrieve menu items', async () => {
    await expectArrayRetrival(
      menuApi,
      id => restaurantApi.getMenuItems(id),
      getFakeMenuItem,
      getFakeMenuItem2,
    );
  });
});

function getFakeMenuItem(restaurant: Restaurant['id']): MenuItem {
  return {
    id: fakeId('MenuItem'),
    restaurant,
    name: 'Fake Cheeseburger',
  };
}

function getFakeMenuItem2(restaurant: Restaurant['id']): MenuItem {
  return {
    id: fakeId('MenuItem'),
    restaurant,
    name: 'Shrimp Fried Rice',
  };
}

describe('/menu endpoint', () => {
  it('can create and read back a menu item', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectReadbackCreated(menuApi, getFakeMenuItem(restaurant));
  });

  it('is gone after it is deleted', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectMissingDeleted(menuApi, getFakeMenuItem(restaurant));
  });

  it('is changed after it is updated', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectUpdatedChanged(
      menuApi,
      getFakeMenuItem(restaurant),
      getFakeMenuItem2(restaurant),
    );
  });

  it('fails to create duplicate', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await checkedCreate(menuApi, getFakeMenuItem(restaurant));
    expect(await menuApi.create(getFakeMenuItem(restaurant))).toBe(null);
  });

  it('fails to update to duplicate', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await checkedCreate(menuApi, getFakeMenuItem(restaurant));
    const itemId = await checkedCreate(menuApi, getFakeMenuItem2(restaurant));
    const item = await menuApi.read(itemId);
    if (item === null) {
      expect.fail('menu item must not be null');
    }
    const updated = await menuApi.update({
      ...item,
      name: getFakeMenuItem(restaurant).name,
    });
    expect(updated).toBe(false);
  });
});

function getFakeDriver(restaurant: Restaurant['id']): Driver {
  return {
    id: fakeId('Driver'),
    name: 'Delano',
    onShift: true,
    phoneNumber: {compact: '9817235273'},
    restaurant,
  };
}

function getFakeDriver2(restaurant: Restaurant['id']): Driver {
  return {
    id: fakeId('Driver'),
    name: 'Qalid',
    onShift: false,
    phoneNumber: {compact: '9817237651'},
    restaurant,
  };
}

describe('/driver endpoint', () => {
  it('can create and read back a driver', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectReadbackCreated(driverApi, getFakeDriver(restaurant));
  });

  it('is gone after it is deleted', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectMissingDeleted(driverApi, getFakeDriver(restaurant));
  });

  it('is changed after it is updated', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectUpdatedChanged(
      driverApi,
      getFakeDriver(restaurant),
      getFakeDriver2(restaurant),
    );
  });

  it('can toggle shift', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const driver = await checkedCreate(driverApi, getFakeDriver(restaurant));
    for (const option of [false, true]) {
      const shifted = await driverApi.setOnShift(driver, option);
      expect(shifted).toBe(true);
      const readback = await driverApi.read(driver);
      expect(readback?.onShift).toBe(option);
    }
  });
});

function getFakeOrder(restaurant: Restaurant['id']): Order {
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

const tryAdvanceOrder = async (
  state: Order['state'],
  nextState: Order['state'],
  works: boolean,
) => {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
  const order = await checkedCreate<Order>(orderApi, {
    ...getFakeOrder(restaurant),
    state,
  });
  expect((await orderApi.read(order))?.state).toBe(state);
  const advanced = await orderApi.advanceState(order);
  expect(advanced).toBe(works);
  expect((await orderApi.read(order))?.state).toBe(works ? nextState : state);
};

describe('/order endpoint', () => {
  it('can create and read back an order', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectReadbackCreated(orderApi, getFakeOrder(restaurant));
  });

  it('is gone after it is deleted', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    await expectMissingDeleted(orderApi, getFakeOrder(restaurant), false);
  });

  it('is high priority after being remade', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const order = await checkedCreate(orderApi, getFakeOrder(restaurant));
    const original = await orderApi.read(order);
    const remade = await orderApi.remake(order);
    expect(remade).toBe(true);
    const readback = await orderApi.read(order);
    expect(readback?.highPriority).toBe(true);
    expect(readback?.itemNames).toEqual(original?.itemNames);
  });

  it('can fail to remake a non-existent order', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const order = await checkedCreate(orderApi, getFakeOrder(restaurant));
    expect(await orderApi.delete(order)).toBe(true);
    expect(await orderApi.remake(order)).toBe(false);
  });

  it('advances when advanced', async () => {
    await tryAdvanceOrder('cooking', 'cooked', true);
  });

  it("doesn't advance when >= cooked", async () => {
    await tryAdvanceOrder('cooked', 'driving', false);
    await tryAdvanceOrder('driving', 'delivered', false);
  });

  it('can update cooked time', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const order = await checkedCreate(orderApi, getFakeOrder(restaurant));
    const cookedTime = new Date(Date.now() + 1e3);
    const updated = await orderApi.updateCookedTime(order, cookedTime);
    expect(updated).toBe(true);
    expect((await orderApi.read(order))?.cookedTime).toEqual(cookedTime);
  });

  it('can fail to update cooked time', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const order = await checkedCreate(orderApi, getFakeOrder(restaurant));
    expect(await orderApi.delete(order)).toBe(true);
    const cookedTime = new Date(Date.now() + 1e3);
    const updated = await orderApi.updateCookedTime(order, cookedTime);
    expect(updated).toBe(false);
  });
});
