import {describe, expect, it} from 'vitest';
import {
  batch,
  driver,
  menuItem,
  order,
  restaurant,
  type JSONDomainObject,
  type JSONParserPair,
} from '~/domain/json';

function testPair<T>(
  parserPair: JSONParserPair<T>,
  json: JSONDomainObject<T>,
  domainObject: T,
  reversable: boolean = true,
) {
  expect(parserPair.parse(json)).toEqual(domainObject);
  if (reversable) expect(parserPair.unparse(domainObject)).toEqual(json);
}

describe('Restaurant parsing', () => {
  it('parses a valid Restaurant', () => {
    testPair(
      restaurant,
      {
        id: 5,
        location: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        name: 'The White House',
      },
      {
        id: {
          type: 'Restaurant',
          id: 5,
        },
        location: {
          address: '1600 Pennsylvania Avenue NW, Washington, DC 20500',
        },
        name: 'The White House',
      },
    );
  });

  it('survives an invalid WorldLocation', () => {
    testPair(
      restaurant,
      {
        id: 173,
        location: '978164HKASDG&Q@^',
        name: 'The White House',
      },
      {
        id: {
          type: 'Restaurant',
          id: 173,
        },
        location: {
          address: '978164HKASDG&Q@^',
        },
        name: 'The White House',
      },
    );
  });
});

describe('Driver parsing', () => {
  it('parses a valid Driver', () => {
    testPair(
      driver,
      {
        id: 98712,
        phoneNumber: '9782372819',
        restaurant: 1238,
        name: 'Jane Doe',
        onShift: false,
      },
      {
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
      },
    );
  });
});

describe('Order parsing', () => {
  it('parses a valid Order', () => {
    testPair(
      order,
      {
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
      },
      {
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
      },
    );
  });

  it('survives a null batch', () => {
    testPair(
      order,
      {
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
      },
      {
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
      },
    );
  });

  it('survives an invalid date', () => {
    testPair(
      order,
      {
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
      },
      {
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
      },
      false,
    );
  });
});

describe('Batch parsing', () => {
  it('parses a valid Batch', () => {
    testPair(
      batch,
      {
        id: 871634,
        driver: 512387,
        route: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
        dispatchTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
        expectedCompletionTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
      },
      {
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
      },
    );
  });

  it('survives an invalid Polyline', () => {
    testPair(
      batch,
      {
        id: 871634,
        driver: 512387,
        route: 'Hello World! 1 2 3 4 5',
        dispatchTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
        expectedCompletionTime: 'Fri, 30 Jan 2026 01:30:00 GMT',
      },
      {
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
      },
    );
  });
});

describe('MenuItem parsing', () => {
  it('parses a valid MenuItem', () => {
    testPair(
      menuItem,
      {
        id: 5123,
        restaurant: 9123,
        name: 'Eggs Benedict',
      },
      {
        id: {
          type: 'MenuItem',
          id: 5123,
        },
        restaurant: {
          type: 'Restaurant',
          id: 9123,
        },
        name: 'Eggs Benedict',
      },
    );
  });
});
