import type {Driver, MenuItem, Restaurant} from '~/domain/objects';

const restaurantId = {type: 'Restaurant' as const, id: 1};

export const initialDrivers: Driver[] = [
  {
    id: {type: 'Driver', id: 1},
    name: 'Ben',
    phoneNumber: {compact: '2061234567'},
    restaurant: restaurantId,
    onShift: true,
  },
  {
    id: {type: 'Driver', id: 2},
    name: 'Delano',
    phoneNumber: {compact: '2067891234'},
    restaurant: restaurantId,
    onShift: true,
  },
  {
    id: {type: 'Driver', id: 3},
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
    id: {type: 'MenuItem', id: 1},
    restaurant: restaurantId,
    name: 'Burger',
  },
  {
    id: {type: 'MenuItem', id: 2},
    restaurant: restaurantId,
    name: 'Pizza',
  },
  {
    id: {type: 'MenuItem', id: 3},
    restaurant: restaurantId,
    name: 'Sandwich',
  },
];
