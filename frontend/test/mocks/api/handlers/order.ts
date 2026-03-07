import {http, sse} from 'msw';
import {
  asId,
  badRequest,
  db,
  endpoint,
  makeCrudHandlers,
  noContent,
  notFound,
} from '../common';
import {isStateBefore, nextStateAfter, type Order} from '~/domain/objects';
import * as json from '~/domain/json';

if (!globalThis.EventSource) {
  // this allows the server-side portion of the MSW SSE mock to run without error
  Object.defineProperty(globalThis, 'EventSource', {
    value: class EventSource {},
  });
}

export const orderHandlers = [
  ...makeCrudHandlers('/order', db.orders, ['create', 'read', 'delete']),
  http.put(endpoint('/order/:id/advance'), req => {
    const order = db.orders.get(asId<Order>(req.params.id));
    if (!order) return notFound('order');
    const parsedState = json.order.field('state').parse(order.state);
    if (!isStateBefore(parsedState, 'cooked')) return badRequest();

    const newOrder: Order = {
      ...json.order.parse(order),
      state: nextStateAfter(parsedState),
    };

    if (newOrder.state === 'cooked') {
      newOrder.cookedTime = new Date();
    }

    db.orders.update(json.order.unparse(newOrder));

    return noContent();
  }),
  http.put(endpoint('/order/:id/delivered/:token'), async req => {
    const orderId = asId<Order>(req.params.id);
    const driverId = asId<Order>(req.params.token);

    // retrieve initial order state
    const order = db.orders.get(orderId);
    if (!order) return notFound('order');

    // get order's batch
    const {currentBatch} = order;
    if (!currentBatch) return notFound("order's batch");
    const batch = db.batches.get(currentBatch);
    if (!batch) return notFound('batch');

    // get driver
    const {driver} = json.batch.parse(batch);
    if (driver.id !== driverId) return badRequest();

    // update order state
    if (
      !db.orders.update({
        ...order,
        state: 'DELIVERED',
      })
    ) {
      return badRequest();
    }

    return noContent();
  }),
  http.put(endpoint('/order/:id/cookedTime'), async req => {
    const order = db.orders.get(asId<Order>(req.params.id));
    if (!order) return notFound('order');
    const time = (await req.request.json()) as string;
    db.orders.update({
      ...order,
      cookedTime: time,
    });
    return noContent();
  }),
  http.put(endpoint('/order/:id/remake'), async req => {
    const order = db.orders.get(asId<Order>(req.params.id));
    if (!order) return notFound('order');

    const domain = json.order.parse(order);
    const now = Date.now();
    db.orders.update(
      json.order.unparse({
        ...domain,
        state: 'cooking',
        initialTime: new Date(now),
        currentBatch: null,
        highPriority: true,
        cookedTime: new Date(
          now + domain.cookedTime.getTime() - domain.initialTime.getTime(),
        ),
        deliveryTime: new Date(
          now + domain.deliveryTime.getTime() - domain.initialTime.getTime(),
        ),
      }),
    );

    return noContent();
  }),
  sse<{refresh: string}>('/sse/orders/:id', async ({client}) => {
    db.orders.addEventListener('change', () => {
      client.send({
        event: 'refresh',
        data: '<<this should never matter/restaurant>>',
      });
    });
  }),
  sse<{refresh: string}>('/sse/orders/token/:token', async ({client}) => {
    db.orders.addEventListener('change', () => {
      client.send({
        event: 'refresh',
        data: '<<this should never matter/driver>>',
      });
    });
  }),
];
