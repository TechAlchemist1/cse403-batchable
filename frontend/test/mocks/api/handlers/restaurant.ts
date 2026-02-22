import {http, HttpResponse} from 'msw';
import {asId, db, endpoint, makeCrudHandlers} from '../common';
import type {Restaurant} from '~/domain/objects';

export const restaurantHandlers = [
  ...makeCrudHandlers('/restaurant', db.restaurants),
  http.get(endpoint('/restaurant/:id/drivers'), req => {
    return HttpResponse.json(
      db.drivers.findMatching('restaurant', asId<Restaurant>(req.params.id)),
    );
  }),
  http.get(endpoint('/restaurant/:id/orders'), req => {
    return HttpResponse.json(
      db.orders
        .findMatching('restaurant', asId<Restaurant>(req.params.id))
        .filter(order => order.state !== 'DELIVERED'),
    );
  }),
  http.get(endpoint('/restaurant/:id/menu'), req => {
    return HttpResponse.json(
      db.menuItems.findMatching('restaurant', asId<Restaurant>(req.params.id)),
    );
  }),
];
