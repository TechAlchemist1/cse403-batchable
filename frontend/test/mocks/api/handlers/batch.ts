import {http, HttpResponse} from 'msw';
import {db, endpoint, makeCrudHandlers, asId, notFound} from '../common';
import type {Batch} from '~/domain/objects';

export const batchHandlers = [
  ...makeCrudHandlers('/order/batch', db.batches, ['read']),
  http.get(endpoint('/order/batch/:id/orders'), req => {
    const id = asId<Batch>(req.params.id);
    if (!db.batches.get(id)) return notFound('/batch');
    const orders = db.orders.findAll(order => order.currentBatch?.id === id);
    return HttpResponse.json(orders);
  }),
];
