import {batchHandlers} from './handlers/batch';
import {driverHandlers} from './handlers/driver';
import {menuHandlers} from './handlers/menu';
import {orderHandlers} from './handlers/order';
import {restaurantHandlers} from './handlers/restaurant';

export const handlers = [
  ...restaurantHandlers,
  ...driverHandlers,
  ...orderHandlers,
  ...batchHandlers,
  ...menuHandlers,
];
