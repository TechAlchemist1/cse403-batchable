import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from '@testing-library/react';
import {http} from 'msw';
import {badRequest, endpoint} from 'test/mocks/api/common';
import {server} from 'test/mocks/api/server';
import {describe, it, expect, vi, beforeEach} from 'vitest';
import {orderApi} from '~/api/endpoints/order';
import {restaurantApi} from '~/api/endpoints/restaurant';
import EditOrderModal from '~/components/dashboard/EditOrderModal';
import {useModal} from '~/components/Modal';
import {fakeId, type Order} from '~/domain/objects';
import {MS_PER_MINUTE} from '~/util/time';

async function createOrder(orderConfig: Partial<Order> = {}) {
  const restaurantId = await restaurantApi.create({
    id: fakeId('Restaurant'),
    location: {address: 'Portland, Oregon'},
    name: 'Batchable Kitchen',
  });

  if (restaurantId === null) {
    expect.fail('new restaurant id must not be null');
  }

  const now = new Date('2024-01-24T05:13:00.000Z');
  const orderId = await orderApi.create({
    id: fakeId('Order'),
    initialTime: now,
    cookedTime: new Date(now.getTime() + 15 * MS_PER_MINUTE),
    deliveryTime: new Date(now.getTime() + 45 * MS_PER_MINUTE),
    currentBatch: null,
    destination: {address: 'Seattle, WA 98105'},
    highPriority: false,
    itemNames: ['Burger', 'Cheese'],
    restaurant: restaurantId,
    state: 'cooking',
    ...orderConfig,
  });

  if (orderId === null) {
    expect.fail('new order id must not be null');
  }

  const order = await orderApi.read(orderId);

  if (order === null) {
    expect.fail('new order must not be null');
  }

  return order;
}

const renderModal = (order: Order) => {
  const state = renderHook(() => useModal());
  act(() => state.result.current.setOpen(true));
  render(<EditOrderModal order={order} state={state.result.current} />);
  return () => state.result.current;
};

describe('<EditOrderModal>', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  it('contains appropriate UI', async () => {
    const order = await createOrder();
    renderModal(order);

    // buttons
    expect(
      screen.getByRole('button', {name: /Cancel Order/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Remake Order/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^Cancel$/i})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Apply Changes/i}),
    ).toBeInTheDocument();

    // order info
    expect(screen.getByText(/Items: /i)).toBeInTheDocument();
    expect(screen.getByText(/Destination: /i)).toBeInTheDocument();

    // inputs
    expect(screen.getByText(/Prep Time \(min\)/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/cooking/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/cooked/i)).toBeInTheDocument();
  });

  it('changes form when >= cooked', async () => {
    const order = await createOrder({state: 'cooked'});
    renderModal(order);

    // buttons
    expect(screen.getByText(/^Cancel$/i)).toBeInTheDocument();
    expect(screen.getByText(/^OK$/i)).toBeInTheDocument();
    expect(screen.getByText(/^cooked$/i)).toBeInTheDocument();
  });

  it('can update order cooked time', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    const prepMins = 300;
    fireEvent.change(screen.getByLabelText(/prep time/i), {
      target: {value: String(prepMins)},
    });

    fireEvent.click(screen.getByText(/Apply Changes/i));

    const expectedCookedTime =
      order.initialTime.getTime() / MS_PER_MINUTE + prepMins;

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      const changed = await orderApi.read(order.id);
      if (changed === null) {
        expect.fail('changed order should exist');
      }

      const cookedTime = changed.cookedTime.getTime() / MS_PER_MINUTE;

      expect(cookedTime).toBeCloseTo(expectedCookedTime, 0);
    });
  });

  it('can fail to update order cooked time', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    const prepMins = 300;
    fireEvent.change(screen.getByLabelText(/prep time/i), {
      target: {value: String(prepMins)},
    });

    server.use(
      http.put(endpoint(`/order/${order.id.id}/cookedTime`), () =>
        badRequest(),
      ),
    );
    fireEvent.click(screen.getByText(/Apply Changes/i));

    await waitFor(async () => {
      expect(getState().open).toBe(false);
      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('can update order state', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    fireEvent.click(screen.getByLabelText(/^cooked$/i));

    await waitFor(() => {
      expect(screen.getByLabelText(/^cooked$/i)).toBeChecked();
    });

    fireEvent.click(screen.getByRole('button', {name: /Apply Changes/i}));

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      const changed = await orderApi.read(order.id);
      if (changed === null) {
        expect.fail('changed order should exist');
      }

      expect(changed.state).toBe('cooked');
    });
  });

  it('can fail to update order state', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    fireEvent.click(screen.getByLabelText(/^cooked$/i));

    await waitFor(() => {
      expect(screen.getByLabelText(/^cooked$/i)).toBeChecked();
    });

    server.use(
      http.put(endpoint(`/order/${order.id.id}/advance`), () => badRequest()),
    );
    fireEvent.click(screen.getByRole('button', {name: /Apply Changes/i}));

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('can remake an order', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    fireEvent.click(screen.getByRole('button', {name: /remake/i}));

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      const changed = await orderApi.read(order.id);
      if (changed === null) {
        expect.fail('changed order should exist');
      }

      expect(changed.highPriority).toBe(true);
    });
  });

  it('can cancel an order', async () => {
    const order = await createOrder();
    const getState = renderModal(order);

    fireEvent.click(screen.getByRole('button', {name: /cancel order/i}));

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      const exists = await orderApi.exists(order.id);
      expect(exists).toBe(false);
    });
  });

  it('cannot edit >= cooked', async () => {
    const order = await createOrder({state: 'driving'});
    const getState = renderModal(order);

    fireEvent.click(screen.getByRole('button', {name: /ok/i}));

    await waitFor(async () => {
      expect(getState().open).toBe(false);

      const changed = await orderApi.read(order.id);
      expect(changed).toEqual(order);
    });
  });

  it('can fail to remake', async () => {
    const order = await createOrder({state: 'cooked'});
    renderModal(order);

    server.use(
      http.put(endpoint(`/order/${order.id.id}/remake`), () => badRequest()),
    );
    fireEvent.click(screen.getByRole('button', {name: /remake order/i}));

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('can fail to cancel', async () => {
    const order = await createOrder({state: 'cooking'});
    renderModal(order);

    server.use(
      http.delete(endpoint(`/order/${order.id.id}`), () => badRequest()),
    );
    fireEvent.click(screen.getByRole('button', {name: /cancel order/i}));

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });
});
