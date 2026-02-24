import {screen, render, fireEvent} from '@testing-library/react';
import {
  checkedCreate,
  getFakeOrder,
  getFakeRestaurant,
} from 'test/mocks/domain_objects';
import {describe, it, expect} from 'vitest';
import {orderApi} from '~/api/endpoints/order';
import {restaurantApi} from '~/api/endpoints/restaurant';
import OrderCard from '~/components/dashboard/OrderCard';
import type {Order} from '~/domain/objects';
import {MS_PER_MINUTE} from '~/util/time';

async function createOrder(orderConfig: Partial<Order> = {}) {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
  const order = await checkedCreate(orderApi, {
    ...getFakeOrder(restaurant),
    ...orderConfig,
  });

  const orderData = await orderApi.read(order);
  if (orderData === null) {
    expect.fail('order data must not be null');
  }

  return orderData;
}

describe('<OrderCard>', () => {
  it('shows the order information', async () => {
    const now = Date.now();
    const order = await createOrder({
      cookedTime: new Date(now + 15 * MS_PER_MINUTE),
      deliveryTime: new Date(now + 30 * MS_PER_MINUTE),
    });
    render(<OrderCard order={order} />);

    expect(screen.getByText(`Order #${order.id.id}`)).toBeInTheDocument();
    expect(
      screen.getByText(/prepared in.*?(14|15).*?min/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/prepared in.*?(29|30).*?min/i),
    ).toBeInTheDocument();
  });

  it('opens the associated edit modal', async () => {
    await createOrder({});
    const order = await createOrder({});
    render(<OrderCard order={order} />);

    fireEvent.click(screen.getByRole('button', {name: /order/i}));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(await screen.findByText(`Edit Order #${order.id.id}`));
  });
});
