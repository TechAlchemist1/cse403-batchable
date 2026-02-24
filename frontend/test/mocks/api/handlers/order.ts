import {http, HttpResponse, ws} from 'msw';
import {
  asId,
  db,
  endpoint,
  makeCrudHandlers,
  noContent,
  notFound,
} from '../common';
import {isStateBefore, nextStateAfter, type Order} from '~/domain/objects';
import * as json from '~/domain/json';
import {StatusCodes} from 'http-status-codes';

const refreshSocket = ws.link(endpoint('/topic/orders/:id', 'ws'));

export const orderHandlers = [
  ...makeCrudHandlers('/order', db.orders, ['create', 'read', 'delete']),
  http.put(endpoint('/order/:id/advance'), req => {
    const order = db.orders.get(asId<Order>(req.params.id));
    if (!order) return notFound('order');
    const parsedState = json.order.field('state').parse(order.state);
    if (!isStateBefore(parsedState, 'cooked')) {
      return HttpResponse.text('Cannot advance past cooked', {
        status: StatusCodes.BAD_REQUEST,
      });
    }
    db.orders.update({
      ...order,
      state: json.order.field('state').unparse(nextStateAfter(parsedState)),
    });
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
    db.orders.update(
      json.order.unparse({
        ...domain,
        state: 'cooking',
        initialTime: new Date(),
        currentBatch: null,
        highPriority: true,
        cookedTime: new Date(
          domain.cookedTime.getTime() - domain.initialTime.getTime(),
        ),
        deliveryTime: new Date(
          domain.deliveryTime.getTime() - domain.initialTime.getTime(),
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
