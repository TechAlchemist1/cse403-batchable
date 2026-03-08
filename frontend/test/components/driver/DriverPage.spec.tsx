// import {mockFailure, mockNoResponse, mockSuccess} from 'test/mocks/query';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {db} from 'test/mocks/api/common';
import {
  checkedCreate,
  checkedCreateBatch,
  getFakeDriver,
  getFakeOrder,
  getFakeRestaurant,
} from 'test/mocks/domain_objects';
import {describe, expect, it, vi, beforeEach} from 'vitest';
import {driverApi} from '~/api/endpoints/driver';
import {orderApi} from '~/api/endpoints/order';
import {restaurantApi} from '~/api/endpoints/restaurant';
import DriverPage from '~/components/driver/DriverPage';
import {DriverTokenContext} from '~/components/DriverTokenContext';
import type {Batch, Driver} from '~/domain/objects';
import {formatOrderName, formatPhoneNumber} from '~/util/format';
import * as json from '~/domain/json';
import {batchApi} from '~/api/endpoints/batch';
import {OrderRefreshContext} from '~/components/OrderRefreshProvider';
import {MockOrderRefresher} from 'test/mocks/refresher';

async function prepareDriver() {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
  const driverId = await checkedCreate(driverApi, getFakeDriver(restaurant));
  const driver = await driverApi.read(driverId);
  if (!driver) expect.fail('driver should not be null');
  return {driver, token: String(driverId.id)};
}

async function createOrderInBatch(driver: Driver, batch: Batch['id']) {
  const orderId = await checkedCreate(
    orderApi,
    getFakeOrder(driver.restaurant),
  );
  const order = await orderApi.read(orderId);
  if (!order) expect.fail('order should not be null');
  order.currentBatch = batch;
  expect(db.orders.update(json.order.unparse(order))).toBe(true);
  return order;
}

function renderPage(token: string) {
  render(
    <DriverTokenContext value={token}>
      <DriverPage />
    </DriverTokenContext>,
  );
}

describe('<DriverPage>', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(window, 'alert').mockImplementation(() => {});
  });
  it('shows the driver name', async () => {
    const {token, driver} = await prepareDriver();
    renderPage(token);

    expect(
      await screen.findByText(driver.name, {exact: false}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(formatPhoneNumber(driver.phoneNumber), {exact: false}),
    ).toBeInTheDocument();
  });

  it('shows no active batch', async () => {
    const {token} = await prepareDriver();
    renderPage(token);

    expect(await screen.findByText(/no.*?batch/i)).toBeInTheDocument();
  });

  it('continues loading with no token', async () => {
    render(<DriverPage />);

    expect(await screen.findByText(/loading/i)).toBeInTheDocument();
  });

  it('shows an error when loading fails', async () => {
    renderPage('100'); // not a valid token

    expect(await screen.findByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows active batch', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);
    const order = await createOrderInBatch(driver, batch);

    renderPage(token);

    expect(
      await screen.findByText(formatOrderName(order), {exact: false}),
    ).toBeInTheDocument();
  });

  it('shows active empty batch', async () => {
    const {driver, token} = await prepareDriver();
    await checkedCreateBatch(driver.id);

    renderPage(token);

    expect(
      await screen.findByRole('button', {
        name: /complete route/i,
      }),
    ).toBeInTheDocument();
  });

  it('can refresh', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);
    await createOrderInBatch(driver, batch);

    const refresher = MockOrderRefresher.create();

    render(
      <DriverTokenContext value={token}>
        <OrderRefreshContext value={refresher}>
          <DriverPage />
        </OrderRefreshContext>
      </DriverTokenContext>,
    );

    refresher.refresh();

    expect(db.batches.delete(batch.id)).toBe(true);

    expect(await screen.findByText(/no.*?batch/i)).toBeInTheDocument();
  });

  it('can deliver orders', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);
    await createOrderInBatch(driver, batch);

    const refresher = MockOrderRefresher.create();

    render(
      <DriverTokenContext value={token}>
        <OrderRefreshContext value={refresher}>
          <DriverPage />
        </OrderRefreshContext>
      </DriverTokenContext>,
    );

    const button = await screen.findByRole('button', {name: /deliver\b/i});
    expect(button).toBeInTheDocument();
    fireEvent.click(button);

    await waitFor(async () => {
      const orders = await batchApi.getOrders(batch);
      if (!orders) expect.fail('orders should not be null');
      expect(orders[0].state).toBe('delivered');
    });

    refresher.refresh();

    await waitFor(() => {
      expect(screen.getByText(/delivered\b/i)).toBeInTheDocument();
      expect(window.alert).not.toHaveBeenCalled();
    });
  });

  it('can fail to deliver orders', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);
    await createOrderInBatch(driver, batch);

    const refresher = MockOrderRefresher.create();

    render(
      <DriverTokenContext value={token}>
        <OrderRefreshContext value={refresher}>
          <DriverPage />
        </OrderRefreshContext>
      </DriverTokenContext>,
    );

    const button = await screen.findByRole('button', {name: /deliver\b/i});
    expect(button).toBeInTheDocument();

    expect(db.batches.delete(batch.id)).toBe(true);

    fireEvent.click(button);

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });

  it('can complete the batch', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);
    await createOrderInBatch(driver, batch);

    renderPage(token);

    const completeButton = await screen.findByRole('button', {
      name: /complete route/i,
    });
    expect(completeButton).toBeInTheDocument();

    fireEvent.click(completeButton);

    await waitFor(async () => {
      expect((await batchApi.read(batch))?.finished).toBe(true);
      expect(await driverApi.getBatch(driver.id)).toBe(null);
    });
  });

  it('can fail to complete the batch', async () => {
    const {driver, token} = await prepareDriver();
    const batch = await checkedCreateBatch(driver.id);

    renderPage(token);

    const completeButton = await screen.findByRole('button', {
      name: /complete route/i,
    });
    expect(completeButton).toBeInTheDocument();

    expect(db.batches.delete(batch.id)).toBe(true);

    fireEvent.click(completeButton);

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalled();
    });
  });
});
