import {
  fakeId,
  type Driver,
  type MenuItem,
  type Restaurant,
} from '~/domain/objects';

const restaurantId: Restaurant['id'] = {type: 'Restaurant', id: 1};

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
    address: '1234 UW Ave, Seattle, WA 98122',
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
