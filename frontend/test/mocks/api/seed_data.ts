import {
  fakeId,
  type Batch,
  type Driver,
  type MenuItem,
  type Order,
  type Restaurant,
} from '~/domain/objects';
import {MS_PER_MINUTE} from '~/util/time';

/**
 * This data is used to populate the mock database when the app is being run in development (npm run dev).
 * During testing, the database is initially empty.
 */
const restaurantId: Restaurant['id'] = {type: 'Restaurant', id: 1};
const now = Date.now();

const minutesFromNow = (minutes: number) =>
  new Date(now + minutes * MS_PER_MINUTE);

export const initialDrivers: Driver[] = [
  {
    id: fakeId('Driver'),
    name: 'Ben',
    phoneNumber: {compact: '2061234567'},
    restaurant: restaurantId,
    onShift: true,
  },
  {
    id: fakeId('Driver'),
    name: 'Delano',
    phoneNumber: {compact: '2067891234'},
    restaurant: restaurantId,
    onShift: true,
  },
  {
    id: fakeId('Driver'),
    name: 'H',
    phoneNumber: {compact: '2061231234'},
    restaurant: restaurantId,
    onShift: false,
  },
];

export const initialRestaurant: Restaurant = {
  id: restaurantId,
  name: 'Batchable Kitchen',
  location: {
    address: '3820 Rainier Ave S, Seattle, WA 98118',
  },
};

export const initialMenuItems: MenuItem[] = [
  {
    id: fakeId('MenuItem'),
    restaurant: restaurantId,
    name: 'Burger',
  },
  {
    id: fakeId('MenuItem'),
    restaurant: restaurantId,
    name: 'Pizza',
  },
  {
    id: fakeId('MenuItem'),
    restaurant: restaurantId,
    name: 'Sandwich',
  },
];

export const initialBatches: Batch[] = [
  {
    id: fakeId('Batch'),
    driver: initialDrivers[0].id,
    route: {encoded: 'iziaHtvkiVwKbS}O`G'},
    dispatchTime: minutesFromNow(-5),
    expectedCompletionTime: minutesFromNow(25),
    finished: false,
  },
];

export const initialOrders: Order[] = [
  {
    id: fakeId('Order'),
    restaurant: restaurantId,
    destination: {address: '3513 Rainier Ave S, Seattle, WA 98144'},
    itemNames: ['Burger'],
    initialTime: minutesFromNow(-30),
    cookedTime: minutesFromNow(-20),
    deliveryTime: minutesFromNow(-5),
    state: 'delivered',
    highPriority: false,
    currentBatch: initialBatches[0].id,
  },
  {
    id: fakeId('Order'),
    restaurant: restaurantId,
    destination: {address: '3300 Rainier Ave S, Seattle, WA 98144'},
    itemNames: ['Pizza'],
    initialTime: minutesFromNow(-20),
    cookedTime: minutesFromNow(-10),
    deliveryTime: minutesFromNow(5),
    state: 'driving',
    highPriority: true,
    currentBatch: initialBatches[0].id,
  },
  {
    id: fakeId('Order'),
    restaurant: restaurantId,
    destination: {address: '3201 Hunter Blvd S, Seattle, WA 98144'},
    itemNames: ['Sandwich'],
    initialTime: minutesFromNow(-4),
    cookedTime: minutesFromNow(11),
    deliveryTime: minutesFromNow(35),
    state: 'cooking',
    highPriority: false,
    currentBatch: null,
  },
];
