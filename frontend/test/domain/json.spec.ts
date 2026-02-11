import {describe, expect, it} from 'vitest';
import {
  parseBatch,
  parseDriver,
  parseMenuItem,
  parseOrder,
  parseRestaurant,
} from '~/domain/json';

describe('Restaurant parsing', () => {
  it('parses a valid Restaurant', () => {
    expect(
      parseRestaurant({
        id: 5,
        location: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        name: 'The White House',
      }),
    ).toEqual({
      id: {
        type: 'Restaurant',
        id: 5,
      },
      location: {
        address: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
      },
      name: 'The White House',
    });
  });

  it('survives an invalid WorldLocation', () => {
    expect(
      parseRestaurant({
        id: 173,
        location: '978164HKASDG&Q@^',
        name: 'The White House',
      }),
    ).toEqual({
      id: {
        type: 'Restaurant',
        id: 173,
      },
      location: {
        address: '978164HKASDG&Q@^',
      },
      name: 'The White House',
    });
  });
});

describe('Driver parsing', () => {
  it('parses a valid Driver', () => {
    expect(
      parseDriver({
        id: 98712,
        phoneNumber: '9782372819',
        restaurant: 1238,
        name: 'Jane Doe',
        onShift: false,
      }),
    ).toEqual({
      id: {
        type: 'Driver',
        id: 98712,
      },
      phoneNumber: '9782372819',
      restaurant: {
        type: 'Restaurant',
        id: 1238,
      },
      name: 'Jane Doe',
      onShift: false,
    });
  });
});

describe('Order parsing', () => {
  it('parses a valid Order', () => {
    expect(
      parseOrder({
        id: 8129387,
        restaurant: 124192,
        destination: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        itemNames: ['Tragedy', 'Comedy'],
        initialTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        deliveryTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        cookedTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        state: 'cooked',
        highPriority: true,
        currentBatch: 19237245,
      }),
    ).toEqual({
      id: {
        type: 'Order',
        id: 8129387,
      },
      restaurant: {
        type: 'Restaurant',
        id: 124192,
      },
      destination: {
        address: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
      },
      itemNames: ['Tragedy', 'Comedy'],
      initialTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      deliveryTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      cookedTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      state: 'cooked',
      highPriority: true,
      currentBatch: {
        type: 'Batch',
        id: 19237245,
      },
    });
  });

  it('survives a null batch', () => {
    expect(
      parseOrder({
        id: 8129387,
        restaurant: 124192,
        destination: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        itemNames: [],
        initialTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        deliveryTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        cookedTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        state: 'cooked',
        highPriority: true,
        currentBatch: null,
      }),
    ).toEqual({
      id: {
        type: 'Order',
        id: 8129387,
      },
      restaurant: {
        type: 'Restaurant',
        id: 124192,
      },
      destination: {
        address: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
      },
      itemNames: [],
      initialTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      deliveryTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      cookedTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      state: 'cooked',
      highPriority: true,
      currentBatch: null,
    });
  });

  it('survives an invalid date', () => {
    expect(
      parseOrder({
        id: 8129387,
        restaurant: 124192,
        destination: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        itemNames: ['Tragedy', 'Comedy'],
        initialTime: 'Hello!!!',
        deliveryTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        cookedTime: 'Fri, 29 Aug 2003 08:30:00 GMT',
        state: 'cooked',
        highPriority: true,
        currentBatch: null,
      }),
    ).toEqual({
      id: {
        type: 'Order',
        id: 8129387,
      },
      restaurant: {
        type: 'Restaurant',
        id: 124192,
      },
      destination: {
        address: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
      },
      itemNames: ['Tragedy', 'Comedy'],
      initialTime: new Date(NaN),
      deliveryTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      cookedTime: new Date('Fri, 29 Aug 2003 08:30:00 GMT'),
      state: 'cooked',
      highPriority: true,
      currentBatch: null,
    });
  });
});

describe('Batch parsing', () => {
  it('parses a valid Batch', () => {
    expect(
      parseBatch({
        id: 871634,
        driver: 512387,
        route: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
        dispatchTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
        expectedCompletionTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
      }),
    ).toEqual({
      id: {
        type: 'Batch',
        id: 871634,
      },
      driver: {
        type: 'Driver',
        id: 512387,
      },
      route: {
        encoded: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
      },
      dispatchTime: new Date('Fri, 30 Jan 2026 01:30:00 GMT'),
      expectedCompletionTime: new Date('Fri, 30 Jan 2026 01:30:00 GMT'),
    });
  });

  it('survives an invalid Polyline', () => {
    expect(
      parseBatch({
        id: 871634,
        driver: 512387,
        route: 'Hello World! 1 2 3 4 5',
        dispatchTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
        expectedCompletionTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
      }),
    ).toEqual({
      id: {
        type: 'Batch',
        id: 871634,
      },
      driver: {
        type: 'Driver',
        id: 512387,
      },
      route: {
        encoded: 'Hello World! 1 2 3 4 5',
      },
      dispatchTime: new Date('Fri, 30 Jan 2026 01:30:00 GMT'),
      expectedCompletionTime: new Date('Fri, 30 Jan 2026 01:30:00 GMT'),
    });
  });
});

describe('MenuItem parsing', () => {
  it('parses a valid MenuItem', () => {
    expect(
      parseMenuItem({
        id: 5123,
        restaurant: 9123,
        name: 'Eggs Benedict',
      }),
    ).toEqual({
      id: {
        type: 'MenuItem',
        id: 5123,
      },
      restaurant: {
        type: 'Restaurant',
        id: 9123,
      },
      name: 'Eggs Benedict',
    });
  });
});
