import {http, HttpResponse, ws} from 'msw';
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

const refreshSocket = ws.link(endpoint('/topic/orders/:id', 'ws'));

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
  refreshSocket.addEventListener('connection', async ({client}) => {
    const changeListener = () => {
      client.send('<<this should never matter>>');
    };

    db.orders.addEventListener('change', changeListener);

    client.addEventListener('close', () => {
      db.orders.removeEventListener('change', changeListener);
    });
  }),
];
