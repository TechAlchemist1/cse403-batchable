import {http, HttpResponse} from 'msw';
import {db, endpoint, makeCrudHandlers, asId} from '../common';
import type {Batch} from '~/domain/objects';

export const batchHandlers = [
  ...makeCrudHandlers('/order/batch', db.batches, ['read']),
  http.get(endpoint('/order/batch/:id/orders'), req => {
    const orders = db.orders.findAll(
      order => order.currentBatch?.id === asId<Batch>(req.params.id),
    );
    return HttpResponse.json(orders);
  }),
];
