import {mockFailure, mockNoResponse, mockSuccess} from 'test/mocks/query';
import {describe, it, expect} from 'vitest';
import {render, screen} from '@testing-library/react';
import type {Order} from '~/domain/objects';
import OrderOverview from '~/components/dashboard/OrderOverview';
import {orderApi} from '~/api/endpoints/order';
import {
  checkedCreate,
  getFakeOrder,
  getFakeRestaurant2,
} from 'test/mocks/domain_objects';
import {restaurantApi} from '~/api/endpoints/restaurant';
import {RestaurantContext} from '~/components/RestaurantProvider';
import {server} from 'test/mocks/api/server';
import {http} from 'msw';
import {badRequest, endpoint} from 'test/mocks/api/common';

async function renderOverview(orders: Partial<Order>[]) {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant2());
  for (const order of orders) {
    await checkedCreate(orderApi, {
      ...getFakeOrder(restaurant),
      ...order,
    });
  }
  render(
    <RestaurantContext value={restaurant}>
      <OrderOverview />
    </RestaurantContext>,
  );
}

describe('<OrderOverview>', () => {
  it('is initially loading', async () => {
    await renderOverview([]);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('stays loading when there is no restaurant', async () => {
    mockNoResponse();
    render(<OrderOverview />);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows an error when loading fails', async () => {
    server.use(http.get(endpoint('/restaurant/1/orders'), () => badRequest()));
    mockFailure();
    await renderOverview([]);

    expect(await screen.findByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows orders', async () => {
    mockSuccess();
    await renderOverview([{}, {}, {}]);

    expect(await screen.findByText('Order #1')).toBeInTheDocument();
    expect(await screen.findByText('Order #2')).toBeInTheDocument();
    expect(await screen.findByText('Order #3')).toBeInTheDocument();
  });
});
