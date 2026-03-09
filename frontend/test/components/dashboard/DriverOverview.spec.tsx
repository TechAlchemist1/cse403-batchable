import {mockFailure, mockNoResponse, mockSuccess} from 'test/mocks/query';
import {describe, it, expect} from 'vitest';
import {render, screen} from '@testing-library/react';
import {
  checkedCreate,
  getFakeDriver,
  getFakeRestaurant2,
} from 'test/mocks/domain_objects';
import {restaurantApi} from '~/api/endpoints/restaurant';
import {RestaurantContext} from '~/components/RestaurantProvider';
import DriverOverview from '~/components/dashboard/DriverOverview';
import {driverApi} from '~/api/endpoints/driver';
import type {Driver} from '~/domain/objects';
import {badRequest, endpoint} from 'test/mocks/api/common';
import {server} from 'test/mocks/api/server';
import {http} from 'msw';

async function renderOverview(drivers: Partial<Driver>[]) {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant2());
  for (const driver of drivers) {
    await checkedCreate(driverApi, {
      ...getFakeDriver(restaurant),
      ...driver,
    });
  }
  render(
    <RestaurantContext value={restaurant}>
      <DriverOverview />
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
    render(<DriverOverview />);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows an error when loading fails', async () => {
    server.use(http.get(endpoint('/restaurant/1/drivers'), () => badRequest()));
    mockFailure();
    await renderOverview([]);

    expect(await screen.findByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows drivers', async () => {
    mockSuccess();
    await renderOverview([
      {
        name: 'Alice',
      },
      {
        name: 'Bob',
      },
      {
        name: 'Cae',
      },
    ]);

    expect(await screen.findByText(/alice/i)).toBeInTheDocument();
    expect(await screen.findByText(/bob/i)).toBeInTheDocument();
    expect(await screen.findByText(/cae/i)).toBeInTheDocument();
  });
});
