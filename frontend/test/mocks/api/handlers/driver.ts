import {http, HttpResponse} from 'msw';
import {
  asId,
  db,
  endpoint,
  makeCrudHandlers,
  notFound,
  noContent,
} from '../common';
import type {Driver} from '~/domain/objects';

export const driverHandlers = [
  ...makeCrudHandlers('/driver', db.drivers),
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
    const id = asId<Driver>(req.params.id);
    if (!db.drivers.get(id)) return notFound('/driver');
    const batch = db.batches.findMatching('driver', id)[0] ?? null;
    return HttpResponse.json(batch);
  }),
];
