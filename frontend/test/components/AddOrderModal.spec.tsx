import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from '@testing-library/react';
import {describe, it, expect, vi} from 'vitest';
import {menuApi} from '~/api/endpoints/menu';
import {restaurantApi} from '~/api/endpoints/restaurant';
import AddOrderModal from '~/components/dashboard/AddOrderModal';
import {useModal} from '~/components/Modal';
import {
  checkedCreate,
  getFakeMenuItem,
  getFakeMenuItem2,
  getFakeRestaurant,
} from 'test/mocks/domain_objects';
import type {MenuItem, Restaurant} from '~/domain/objects';
import {RestaurantContext} from '~/components/RestaurantProvider';
import {server} from 'test/mocks/api/server';
import {http} from 'msw';
import {badRequest, endpoint} from 'test/mocks/api/common';

function renderOpenModal(restaurant: Restaurant['id'] | null) {
  const hook = renderHook(() => useModal());
  act(() => hook.result.current.setOpen(true));
  const rendered = render(
    <RestaurantContext value={restaurant}>
      <AddOrderModal modal={hook.result.current} />
    </RestaurantContext>,
  );
  return {
    modal: () => hook.result.current,
    rerender: (rid: Restaurant['id'] | null = restaurant) =>
      rendered.rerender(
        <RestaurantContext value={rid}>
          <AddOrderModal modal={hook.result.current} />
        </RestaurantContext>,
      ),
  };
}

async function tryCreateOrder(
  {
    address,
    prepTime,
    deliveryTime,
  }: {
    address: string;
    prepTime: number;
    deliveryTime: number;
  },
  selectMenuItems: (items: MenuItem[]) => MenuItem[],
) {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
  const menuItem1 = getFakeMenuItem(restaurant);
  const menuItem2 = getFakeMenuItem2(restaurant);
  await checkedCreate(menuApi, menuItem1);
  await checkedCreate(menuApi, menuItem2);

  await waitFor(async () => {
    const menuItems = await restaurantApi.getMenuItems(restaurant);
    expect(menuItems?.length).toBe(2);
  });

  renderOpenModal(restaurant);

  fireEvent.change(screen.getByLabelText(/customer address/i), {
    target: {value: address},
  });

  fireEvent.change(screen.getByLabelText(/prep time/i), {
    target: {value: String(prepTime)},
  });

  fireEvent.change(screen.getByLabelText(/delivery time/i), {
    target: {value: String(deliveryTime)},
  });

  for (const item of selectMenuItems([menuItem1, menuItem2])) {
    fireEvent.click(await screen.findByLabelText(item.name));
  }

  fireEvent.click(screen.getByRole('button', {name: /create new order/i}));

  return restaurant;
}

describe('<AddOrderModal>', () => {
  it('has the input elements', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    renderOpenModal(restaurant);

    // buttons
    expect(
      screen.getByRole('button', {name: /create new order/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /cancel/i})).toBeInTheDocument();

    // fields
    expect(screen.getByLabelText(/customer address/i)).toBeInTheDocument();
    expect(screen.getByText(/^menu items$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/prep time \(min\)/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/delivery time \(min\)/i)).toBeInTheDocument();
  });

  it('can create an order', async () => {
    const ADDRESS = 'Meridian, ID';
    const restaurant = await tryCreateOrder(
      {
        address: ADDRESS,
        prepTime: 15,
        deliveryTime: 40,
      },
      items => [items[1]],
    );
    await waitFor(async () => {
      const orders = await restaurantApi.getOrders(restaurant);

      if (orders === null) {
        expect.fail('orders list must not be null');
      }
      expect(orders.length).toBe(1);
      expect(orders[0].destination.address).toBe(ADDRESS);
    });
  });

  it('fails to add order with no items', async () => {
    window.alert = () => {};
    vi.spyOn(window, 'alert');
    await tryCreateOrder(
      {
        address: 'New York City, NY',
        prepTime: 30,
        deliveryTime: 100,
      },
      () => [],
    );
    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('fails to add order on API failure', async () => {
    window.alert = () => {};
    vi.spyOn(window, 'alert');
    await tryCreateOrder(
      {
        address: 'New York City, NY',
        prepTime: 30,
        deliveryTime: 100,
      },
      items => [items[0]],
    );
    server.use(
      http.post(endpoint('/order'), () => {
        return badRequest();
      }),
    );
    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('can be re-opened', async () => {
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const menuItem = getFakeMenuItem(restaurant);
    await menuApi.create(menuItem);
    const {modal, rerender} = renderOpenModal(restaurant);
    fireEvent.click(await screen.findByLabelText(menuItem.name));
    fireEvent.change(screen.getByLabelText(/customer address/i), {
      target: {value: 'Seattle, WA'},
    });
    fireEvent.click(screen.getByRole('button', {name: /create new order/i}));
    rerender();
    await waitFor(() => {
      expect(modal().open).toBe(false);
      expect(screen.queryByText(/customer address/i)).not.toBeInTheDocument();
    });
    act(() => modal().setOpen(true));
    rerender();
    expect(await screen.findByText(/customer address/i)).toBeInTheDocument();
    expect(await screen.findByLabelText(menuItem.name)).not.toBeChecked();
  });

  it('fails without a restaurant', async () => {
    vi.spyOn(window, 'alert');
    const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
    const menuItem = getFakeMenuItem(restaurant);
    await menuApi.create(menuItem);
    const {rerender} = renderOpenModal(restaurant);
    fireEvent.click(await screen.findByLabelText(menuItem.name));
    fireEvent.change(screen.getByLabelText(/customer address/i), {
      target: {value: 'Seattle, WA'},
    });
    rerender(null);
    fireEvent.click(screen.getByRole('button', {name: /create new order/i}));
    expect(window.alert).toHaveBeenCalled();
  });
});
