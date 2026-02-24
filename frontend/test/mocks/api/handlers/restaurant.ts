import {http, HttpResponse} from 'msw';
import {asId, db, endpoint, makeCrudHandlers, notFound} from '../common';
import type {Restaurant} from '~/domain/objects';

export const restaurantHandlers = [
  ...makeCrudHandlers('/restaurant', db.restaurants),
  http.get(endpoint('/restaurant/:id/drivers'), req => {
    const id = asId<Restaurant>(req.params.id);
    if (!db.restaurants.get(id)) return notFound('/restaurant');
    return HttpResponse.json(
      db.drivers.findMatching('restaurant', asId<Restaurant>(req.params.id)),
    );
  }),
  http.get(endpoint('/restaurant/:id/orders'), req => {
    const id = asId<Restaurant>(req.params.id);
    if (!db.restaurants.get(id)) return notFound('/restaurant');
    return HttpResponse.json(
      db.orders
        .findMatching('restaurant', id)
        .filter(order => order.state !== 'DELIVERED'),
    );
  }),
  http.get(endpoint('/restaurant/:id/menu'), req => {
    const id = asId<Restaurant>(req.params.id);
    if (!db.restaurants.get(id)) return notFound('/restaurant');
    return HttpResponse.json(db.menuItems.findMatching('restaurant', id));
  }),
];
