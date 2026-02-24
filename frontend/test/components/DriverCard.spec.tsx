import {render, screen} from '@testing-library/react';
import {
  checkedCreate,
  getFakeDriver,
  getFakeRestaurant,
} from 'test/mocks/domain_objects';
import {describe, it, expect} from 'vitest';
import {driverApi} from '~/api/endpoints/driver';
import {restaurantApi} from '~/api/endpoints/restaurant';
import DriverCard from '~/components/dashboard/DriverCard';
import {type Driver} from '~/domain/objects';

async function createDriver(driverConfig: Partial<Driver> = {}) {
  const restaurant = await checkedCreate(restaurantApi, getFakeRestaurant());
  const driver = await checkedCreate(driverApi, {
    ...getFakeDriver(restaurant),
    ...driverConfig,
  });
  const driverData = await driverApi.read(driver);
  if (driverData === null) {
    expect.fail('driver data must not be null');
  }
  return driverData;
}

describe('<DriverCard>', () => {
  it("Shows the driver's name", async () => {
    const NAME = 'Xyzw';
    const driver = await createDriver({name: NAME});
    render(<DriverCard driver={driver} />);

    expect(screen.getByText(NAME, {exact: false})).toBeInTheDocument();
  });

  it('shows the driver on shift', async () => {
    const driver = await createDriver({onShift: true});
    render(<DriverCard driver={driver} />);

    expect(screen.getByText(/on shift/i)).toBeInTheDocument();
  });

  it('shows the driver off shift', async () => {
    const driver = await createDriver({onShift: false});
    render(<DriverCard driver={driver} />);

    expect(screen.getByText(/off shift/i)).toBeInTheDocument();
  });

  it("shows the driver's phone number", async () => {
    const driver = await createDriver({
      phoneNumber: {
        compact: '1928731872',
      },
    });
    render(<DriverCard driver={driver} />);

    expect(screen.getByText(/\(?192\)?.873.1872/)).toBeInTheDocument();
  });
});
