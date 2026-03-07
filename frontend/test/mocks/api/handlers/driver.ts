import {http, HttpResponse} from 'msw';
import {
  asId,
  db,
  endpoint,
  makeCrudHandlers,
  notFound,
  noContent,
  badRequest,
} from '../common';
import type {Driver} from '~/domain/objects';
import * as json from '~/domain/json';

function getBatch(driver: json.JSONDomainObject<Driver>) {
  const batch = db.batches.findMatching('driver', driver.id)[0] ?? null;
  return batch && !batch.finished ? batch : null;
}

export const driverHandlers = [
  ...makeCrudHandlers('/driver', db.drivers),
  http.get(endpoint('/driver/route/:token'), req => {
    const id = asId<Driver>(req.params.token); // token === id in mock
    const driver = db.drivers.get(id);
    if (!driver) return notFound('driver');

    // get batch
    const batch = getBatch(driver);
    if (!batch) return HttpResponse.json({driver, orders: null});

    // get batch orders
    const orders = db.orders.findAll(
      order => order.currentBatch?.id === batch.id,
    );
    return HttpResponse.json({
      driver,
      mapLink: 'https://maps.google.com',
      orders,
    });
  }),
  http.put(endpoint('/driver/returned/:token'), req => {
    const driver = db.drivers.get(asId<Driver>(req.params.token)); // token === id in mock
    if (!driver) return notFound('driver');
    const batch = getBatch(driver);
    if (!batch) return notFound('batch');

    if (!db.batches.update({...batch, finished: true})) {
      return badRequest();
    }
    return noContent();
  }),
  http.put(endpoint('/driver/:id/shift'), req => {
    const driver = db.drivers.get(asId<Driver>(req.params.id));
    if (!driver) return notFound('driver');
    const queryParams = new URL(req.request.url).searchParams;
    db.drivers.update({
      ...driver,
      onShift: queryParams.get('onShift') === 'true',
    });
    return noContent();
  }),
  http.get(endpoint('/driver/:id/batch'), req => {
    const driver = db.drivers.get(asId<Driver>(req.params.id));
    if (!driver) return notFound('driver');
    return HttpResponse.json(getBatch(driver));
  }),
];
